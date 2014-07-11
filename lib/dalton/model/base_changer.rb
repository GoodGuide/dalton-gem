module Dalton
  module Model
    class BaseChanger
      attr_reader :id, :original, :changes, :retractions
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

      def generate_datom(key, value, &b)
        case value
        when Enumerable
          (original(key) || []).each do |o|
            yield [:'db/retract', datomic_key(key), o]
          end

          value.each do |v|
            generate_datom(key, v, &b)
          end
        when Model
          # using a hash so that reverse attributes (ns.model/_attr) are supported
          yield(:'db/id' => @id, datomic_key(key) => value.id)
        when Numeric, String, Symbol, true, false
          yield [:'db/add', @id, datomic_key(key), value]
        when BaseChanger
          value.generate_datoms(&b)
          yield(:'db/id' => @id, datomic_key(key) => value.id)
        else
          raise TypeError.new("invalid datomic value: #{value.inspect}")
        end
      end

    protected
      def generate_datoms(&b)
        return enum_for(:generate_datoms).to_a unless block_given?

        yield model.base_attributes.merge(:'db/id' => @id)
        @changes.each do |key, new_val|
          generate_datom(key, new_val, &b)
        end
      end

      def datomic_key(key)
        model.get_attribute(key).to_sym
      end
    end
  end
end

