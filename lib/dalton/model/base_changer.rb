module Dalton
  module Model
    class BaseChanger
      attr_reader :id, :original, :changes, :retractions
      def initialize(id, attrs)
        @id = id
        @original = attrs.dup.freeze
        @changes = {}
        @retractions = Set.new
        @associated_changes = []
      end

      def retract!(attribute)
        @retractions << attribute
      end

      def change(obj=nil, &b)
        if obj
          @associated_changes << obj.change(&b)
        else
          b.call(self)
          self
        end
      end

      def change!(&b)
        change(&b)
        save!
      end

      def [](key)
        return nil if @retractions.include? key
        @changes.fetch(key) { @original[key] }
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
        out = model.attributes.merge(@changes)
        @retractions.each { |r| out.delete(r) }
        out
      end

      def generate_datoms(&b)
        return enum_for(:generate_datoms).to_a unless block_given?

        yield model.base_attributes.merge(:'db/id' => @id)
        @changes.each do |key, new_val|
          attribute = model.get_attribute(key)
          attribute.type.datoms_for(self, new_val, &b)
        end
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
    end
  end
end

