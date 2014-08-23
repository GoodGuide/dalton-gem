require 'logger' # stdlib

require 'dalton/model/schema'
require 'dalton/model/attribute'
require 'dalton/model/base_finder'
require 'dalton/model/base_changer'
require 'dalton/model/validator'

module Dalton
  module Model
    def self.included(base)
      base.class_eval do
        @attributes = {}
        @base_attributes = {}
        @defaults = {}
        @validator = Validator.new(base)

        const_set :Finder, Class.new(BaseFinder) {
          # we use a constant here so that `super` works
          # in overriding generated methods
          const_set :AttributeMethods, Module.new
          include self::AttributeMethods
          define_method(:model) { base }
        }

        const_set :Changer, Class.new(BaseChanger) {
          # as above
          const_set :AttributeMethods, Module.new
          include self::AttributeMethods
          define_method(:model) { base }
        }

        extend Dalton::Model::ClassMethods
      end
    end

    @registry = {}
    @logger = Logger.new($stderr)
    @logger.level = Logger::WARN

    class << self
      attr_reader :registry

      def install_schemas!
        registry.values.each(&:install_schema!)
      end

      def install_bases!
        registry.values.each(&:install_base!)
      end

      def install!
        install_bases!
        install_schemas!
      end

      attr_accessor :namespace, :partition, :uri, :logger
      def configure(&b)
        yield self
      end
    end

    module ClassMethods
      attr_reader :attributes
      attr_reader :defaults
      attr_reader :validator
      attr_reader :datomic_name
      attr_reader :namespace
      attr_reader :partition
      attr_reader :base_attributes

      def transact(edn)
        Model.logger.info("datomic.transact #{Connection.convert_datoms(edn).to_edn}")
        connection.transact(edn)
      end

      def base_attribute(key, val)
        @base_attributes.merge!(key => val)
      end

      def uri(arg=nil)
        @uri = arg if arg
        @uri or Model.uri or raise "you must specify a datomic uri for #{self}"
      end

      def connection
        Connection.connect(uri)
      end

      def datomic_type
        :"#{namespace}.type/#{datomic_name}"
      end

      def datomic_type_key
        :"#{namespace}/type"
      end

      def attribute(attr, datomic_key=nil, opts={})
        if datomic_key.is_a? Hash
          opts = datomic_key
          datomic_key = nil
        end

        datomic_key ||= "#{self.namespace}.#{self.datomic_name}/#{attr.to_s.tr('_', '-')}"
        define_attribute(attr, datomic_key, opts)
      end

      def referenced(name, opts={})
        from_rel = opts.delete(:from) { self.datomic_name }

        namespace = opts.fetch(:namespace) {
          type.respond_to?(:namespace) ? type.namespace : self.namespace
        }

        type = type.datomic_name if type.respond_to? :datomic_name
        define_attribute name, "#{namespace}.#{type}/_#{from_rel}", :default => []
      end

      def define_attribute(key, datomic_key, opts={})
        @attributes[key] = Attribute.new(self, key, opts.merge(datomic_attribute: datomic_key))
        @defaults[key] = opts[:default]

        define_method(key) { self[key] }

        self::Finder::AttributeMethods.class_eval do
          define_method("by_#{key}") { |v| where(key => v) }
        end

        self::Changer::AttributeMethods.class_eval do
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
        self::Changer.new(Dalton::Utility.tempid(partition), defaults).change!(&b)
      end

      def create(&b)
        self::Changer.new(Dalton::Utility.tempid(partition), defaults).change(&b)
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
      definition = self.class.get_attribute(key)

      definition.type.load(entity.get(definition.datomic_attribute))
    end

    def interpret_value(value)
      case value
      when Enumerable
        value.lazy.map { |e| interpret_value(e) }
      when Java::DatomicQuery::EntityMap
        self.class.interpret_entity(value)
      when Numeric, String, Symbol, true, false, nil
        value
      else
        raise TypeError.new("unknown value type: #{value.inspect}")
      end
    end

    def attributes
      out = {}

      self.class.attributes.each do |attr, _|
        out[attr] = send(attr)
      end

      out
    end

    def to_h
      attributes.merge(:id => id)
    end

    # TODO: fix this implementation
    def updated_at
      txid = Peer.q('[:find (max ?t) :in $ ?e :where [?e _ _ ?t]]', entity.db, self.id).first.first
      Time.at(entity.db.entity(txid).get(':db/txInstant').getTime/1000)
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
  end
end
