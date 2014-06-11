module Dalton
  module Model
    def self.included(base)
      base.class_eval do
        @attributes = {}
        @validator = Validator.new(base)

        const_set :Finder, Class.new(BaseFinder) {
          define_method(:model) { base }
        }

        const_set :Changer, Class.new(BaseChanger) {
          define_method(:model) { base }
        }

        extend Dalton::Model::ClassMethods
      end
    end

    module ClassMethods
      attr_reader :attributes
      attr_reader :validator

      def schema(edn)
        @schema = edn
      end

      def install_schema!
        raise ArgumentError.new("no schema defined for #{self}!") unless @schema
        transact(@schema)
      end

      def install_base!
        transact <<-EDN
          [{:db/id #db/id[:db.part/db]
            :db/ident #{partition}
            :db.install/_partition :db.part/db}

           {:db/id #db/id[:db.part/db]
            :db/ident :#{namespace}/type
            :db/valueType :db.type/ref
            :db/cardinality :db.cardinality/one
            :db/doc "A model's type"
            :db.install/_attribute :db.part/db}]
        EDN
      end

      def transact(edn)
        connection.transact(edn)
      end

      def uri(arg=nil)
        @uri = arg if arg
        @uri or raise "you must specify a datomic uri for #{self}"
      end

      def connection
        Dalton::Database.connect(uri)
      end

      def namespace(arg=nil)
        @namespace = arg if arg
        @namespace or raise "you must define a namespace for #{self}"
      end

      def partition(arg=nil)
        @partition = "db.part/#{arg}" if arg
        @partition or raise "you must define a partition for #{self}"
      end

      def datomic_name
        self.name
          .gsub(/[^[:alpha:]]+/, '-')
          .gsub(/(?<=[[:lower:]])(?=[[:upper:]])/, '-')
          .downcase
      end

      def datomic_type
        :"#{namespace}.type/#{datomic_name}"
      end

      def datomic_type_key
        :"#{namespace}/type"
      end

      def attribute(attr, datomic_key=nil)
        datomic_key ||= "#{self.namespace}.#{self.datomic_name}/#{attr.to_s.tr('_', '-')}"
        define_attribute(attr, datomic_key)
      end

      def define_attribute(key, datomic_key)
        @attributes[key] = datomic_key

        define_method(key) { self[key] }

        finders do
          define_method("by_#{key}") { |v| where(key => v) }
        end

        changers do
          define_method(key) { self[key] }
          define_method("#{key}=") { |v| self[key] = v }
        end
      end

      def get_attribute(key)
        @attributes.fetch(key)
      end

      def finders(&b)
        self::Finder.class_eval(&b)
      end

      def changers(&b)
        self::Changer.class_eval(&b)
      end

      def validation(&b)
        @validator.specify(&b)
      end

      def finder(db, constraints=[])
        self::Finder.new(db).where(constraints)
      end

      def create!(&b)
        self::Changer.new(Peer.tempid(partition), {}).change!(&b)
      end
    end

    attr_reader :finder, :entity
    def initialize(entity)
      @entity = entity
      @finder = self.class::Finder.new(entity.db)
    end

    def id
      entity.get(':db/id')
    end

    def [](key)
      datomic_key = self.class.get_attribute(key)
      entity.get(datomic_key)
    end

    def attributes
      out = {}

      self.class.attributes.each do |attr, datomic_attr|
        out[attr] = entity.get(datomic_attr)
      end

      out
    end

    def to_h
      attributes.merge(:id => id)
    end

    def changer
      self.class::Changer.new(id, attributes)
    end

    def change(&b)
      changer.change(&b)
    end

    def change!(&b)
      changer.change!(&b)
    end

    def ==(other)
      self.entity == other.entity
    end

    class BaseFinder
      include Enumerable
      include Dalton::Utility

      attr_reader :db, :constraints
      def initialize(db, constraints=[])
        @db = db
        @constraints = constraints
      end

      def where(*constraints)
        new_constraints = @constraints.dup
        constraints.each do |c|
          case c
          when Array
            new_constraints << c
          when Hash
            interpret_constraints(c, &new_constraints.method(:<<))
          end
        end

        self.class.new(@db, new_constraints)
      end

      def entity(id)
        entity = @db.entity(id)

        unless entity.get(":#{model.datomic_type_key}").to_s[1..-1] == model.datomic_type.to_s
          binding.pry
          raise NotFound.new(model, id)
        end

        model.new(entity)
      end

      def results
        query = [:find, sym('?e'), :in, sym('$'), :where, type_constraint, *constraints]
        q(query).lazy.map do |el|
          model.new(@db.entity(el.first))
        end
      end

      def type_constraint
        [sym('?e'), model.datomic_type_key, model.datomic_type]
      end

      def each(&b)
        results.each(&b)
      end

      def with_model(model)
        model.finder(@db)
      end

    private

      def interpret_constraints(hash, &b)
        return enum_for(:interpret_constraints, hash) unless block_given?

        hash.each do |key, value|
          yield [sym('?e'), model.get_attribute(key).to_sym, value]
        end
      end

      def q(query)
        translated_query = Translation.from_ruby(query)
        $stderr.puts("datomic.q #{translated_query.to_edn}")
        result = Peer.q(translated_query, @db)
        Translation.from_clj(result)
      end
    end

    class BaseChanger
      def initialize(id, attrs)
        @id = id
        @original = attrs.dup.freeze
        @changes = {}
        @retractions = Set.new
      end

      def retract!(attribute)
        @retractions << attribute
      end

      def change(&b)
        b.call(self)
        self
      end

      def change!(&b)
        change(&b)
        save!
      end

      def [](key)
        return nil if @retractions.include? key
        @changes[key] || @original[key]
      end

      def original(key)
        @original[key]
      end

      def change_in(key)
        [original(key), self[key]]
      end

      def []=(key, val)
        @retractions.delete(key)
        @changes[key] = val
      end

      def updated_attributes
        out = @model.attributes.merge(@changes)
        @retractions.each { |r| out.delete(r) }
        out
      end

    private
      def save!
        validate!
        persist!
      end

      def persist!
        result = model.transact(generate_datoms)
        @id = result.resolve_tempid(@id) unless @id.is_a? Fixnum
        model.new(result.db_after.entity(@id))
      end

      def validate!
        model.validator.run_all!(self)
      end

      def interpret_value(value)
        value # TODO
      end

      def generate_datoms(&b)
        return enum_for(:generate_datoms).to_a unless block_given?

        out = {}
        out[:'db/id'] = @id
        out[model.datomic_type_key] = model.datomic_type
        @changes.each do |key, new_val|
          datomic_key = model.get_attribute(key)
          out[datomic_key] = interpret_value(new_val)
        end

        yield out
      end
    end

    class ValidationError < StandardError
      attr_reader :changes, :errors

      def initialize(changes, errors)
        @changes = changes
        @errors = errors
      end

      def errors_on(key, &b)
        return enum_for(:errors_on, key).to_a unless block_given?

        errors.each do |(keys, message)|
          yield message if keys.include? key
        end
      end

      def errors_on?(key)
        errors_on(key).any?
      end
    end

    class NotFound < StandardError
      attr_reader :model, :id
      def initialize(model, id)
        @model = model
        @id = id
      end

      def message
        "Could not find #{model} with id #{id}"
      end
    end

    class Validator
      class Rule
        class Scope
          def initialize(attrs, validate, &report)
            @validate = validate
            @attrs = attrs
            @report = report
          end

          def invalid!(attr_names=nil, description)
            attr_names ||= @attrs
            attr_names = Array(attr_names)

            @report.call [attr_names, description]
          end

          def run(values)
            instance_exec(*values, &@validate)
          end
        end

        def initialize(*attrs, &block)
          @attrs = attrs
          @block = block
        end

        def run(changer, &out)
          values = @attrs.map { |a| changer.send(a) }
          Scope.new(@attrs, @block, &out).run(values)
        end
      end

      attr_reader :validators
      def initialize(model, &defn)
        @model = model
        @validators = []
        specify(&defn) if defn
      end

      def specify(&defn)
        instance_eval(&defn)
      end

      def validate(*attrs, &block)
        validators << Rule.new(*attrs, &block)
      end

      def run_all(changer, &report)
        return enum_for(:run_all, changer).to_a unless block_given?

        validators.each { |v| v.run(changer, &report) }
      end

      def run_all!(changer)
        errors = run_all(changer)
        raise ValidationError.new(changer, errors) if errors.any?
      end
    end
  end
end
