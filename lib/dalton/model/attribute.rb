module Dalton
  module Model
    class Attribute
      attr_reader :name, :model, :datomic_attribute, :type
      def initialize(model, name, opts={})
        @name = name
        @model = model
        @datomic_attribute = opts.fetch(:datomic_attribute) { default_datomic_attribute }
        @type = Type.for(opts[:type])
      end

      def default_datomic_attribute
        "#{model.namespace}.#{model.datomic_name}/#{name.to_s.tr('_', '-')}"
      end

      def load(value)
        type.load(self, value)
      end

      def dump(value)
        type.dump(self, value)
      end

      def datoms_for(changer, value, &b)
        type.datoms_for(self, changer, value, &b)
      end

      class Type
        def self.for(definition)
          return definition if definition.is_a? Type
          return AutoType.new if definition.nil?

          type, *args = definition
          type_name = "#{type.capitalize}Type"
          raise "no such type #{type}" unless const_defined?(type_name)
          const_get(type_name).new(*args)
        end

        def load(attr, value)
          value
        end

        def dump(attr, value)
          value
        end

        def datoms_for(attr, changer, value, &b)
          yield(:'db/id' => changer.id, attr.datomic_attribute => dump(attr, value))
        end

        def invalid_value!(attr, value)
          raise ::TypeError, "invalid value for #{attr.datomic_attribute}: #{value.inspect}"
        end

        class AutoType < Type
          def type_from_value(value)
            case value
            when Enumerable
              SetType.new(self)
            when Dalton::Entity
              RefType.new(raise 'TODO')
            when Numeric, String, Symbol, true, false, nil
              Type.new rescue binding.pry
            else
              raise TypeError.new("unknown value type: #{value.inspect}")
            end
          end

          def load(attr, value)
            type_from_value(value).load(attr, value)
          end

          def dump(attr, value)
            type_from_value(value).dump(attr, value)
          end

          def datoms_for(attr, changer, value, &b)
            type_from_value(value).datoms_for(attr, changer, value, &b)
          end
        end

        class RefType < Type
          attr_reader :ref_class
          def initialize(ref_class)
            @ref_class = ref_class
          end

          def load(attr, entity_map)
            return nil if entity_map.nil?
            registry_name = entity_map.get(attr.model.datomic_type_key)
            invalid_value!(attr, entity_map) unless registry_name == @ref_class.datomic_type
            @ref_class.new(entity_map)
          end

          def dump(attr, value)
            value.id
          end

          def datoms_for(attr, changer, value, &block)
            invalid_value!(attr, value) unless value.respond_to? :id

            yield(:'db/id' => changer.id, attr.datomic_attribute => value.id)

            value.generate_datoms(&block) if value.is_a? BaseChanger
          end
        end

        class SetType < Type
          def initialize(element_type)
            @element_type = Type.for(element_type)
          end

          def dump(attr, value)
            value.map { |x| @element_type.dump(value) }
          end

          def load(attr, value)
            # empty sets are often returned as nil in datomic :[
            return Set.new if value.nil?
            invalid_value!(attr, value) unless value.is_a? Enumerable
            Set.new(value.map { |e| @element_type.load(attr, e) })
          end

          def datoms_for(attr, changer, value, &block)
            value.each do |v|
              @element_type.datoms_for(attr, changer, v, &block)
            end
          end
        end
      end
    end
  end
end
