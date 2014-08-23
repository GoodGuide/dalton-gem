module Dalton
  module Model
    class Attribute
      attr_reader :name, :model, :datomic_attribute, :type
      def initialize(model, name, opts={})
        @name = name
        @model = model
        @datomic_attribute = opts.fetch(:datomic_attribute) { default_datomic_attribute }
        @type = Type.for(self, opts[:type])
      end

      def default_datomic_attribute
        "#{model.namespace}.#{model.datomic_name}/#{name.to_s.tr('_', '-')}"
      end

      class Type
        def self.for(attr, definition)
          return definition if definition.is_a? Type
          return AutoType.new(attr) if definition.nil?

          type, *args = definition
          type_name = "#{type.capitalize}Type"
          raise "no such type #{type}" unless const_defined?(type_name)
          const_get(type_name).new(attr, *args)
        end

        attr_reader :attribute
        def initialize(attr)
          @attribute = attr
        end

        def load(value)
          value
        end

        def dump(value)
          value
        end

        def datoms_for(changer, value, &b)
          yield(:'db/id' => changer.id, @attribute.datomic_attribute => dump(value))
        end

        def invalid_value!(value)
          raise TypeError, "invalid value for #{@attribute.datomic_attribute}: #{value.inspect}"
        end

        class AutoType < Type
          def type_from_value(value)
            case value
            when Enumerable
              SetType.new(@attribute, self)
            when Java::DatomicQuery::EntityMap
              RefType.new(@attribute)
            when Numeric, String, Symbol, true, false, nil
              Type.new(@attribute)
            else
              raise TypeError.new("unknown value type: #{value.inspect}")
            end
          end

          def load(value)
            type_from_value(value).load(value)
          end

          def dump(value)
            type_from_value(value).dump(value)
          end

          def datoms_for(changer, value, &b)
            type_from_value(value).datoms_for(changer, value, &b)
          end
        end

        class RefType < Type
          def initialize(attribute, ref_class)
            @attribute = attribute
            @ref_class = ref_class
          end

          def load(entity_map)
            return nil if entity_map.nil?
            registry_name = entity_map.get(":#{@attribute.model.datomic_type_key}").to_s[1..-1]
            invalid_value!(entity_map) unless registry_name == @ref_class.datomic_type.to_s
            @ref_class.new(entity_map)
          end

          def dump(value)
            value.id
          end

          def datoms_for(changer, value, &block)
            invalid_value!(value) unless value.respond_to? :id

            yield(:'db/id' => changer.id, @attribute.datomic_attribute => value.id)

            value.generate_datoms(&block) if value.is_a? BaseChanger
          end
        end

        class SetType < Type
          def initialize(attribute, element_type)
            @attribute = attribute
            @element_type = Type.for(@attribute, element_type)
          end

          def dump(value)
            value.map { |x| @element_type.dump(value) }
          end

          def load(value)
            # empty sets are often returned as nil in datomic :[
            return Set.new if value.nil?
            invalid_value!(value) unless value.is_a? Enumerable
            Set.new(value.map { |e| @element_type.load(e) })
          end

          def datoms_for(changer, value, &block)
            value.each do |v|
              @element_type.datoms_for(changer, v, &block)
            end
          end
        end
      end
    end
  end
end
