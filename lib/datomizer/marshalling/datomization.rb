require 'erb'
module Datomizer
  module Marshalling
    module Datomization

      module_function

      def collection_to_datoms(data, partition=:'db.part/user')
        case data
          when Hash
            hash_to_datoms(data, partition)
          when Array
            array_to_datoms(data, partition)
          else
            raise ArgumentError, "Datomization not supported for type #{value.class.name}"
        end
      end

      def hash_to_datoms(data, partition=:'db.part/user')
        if data.empty?
          :'ref.map/empty'
        else
          data.map { |key, value|
            {:'db/id' => Datomizer::Database.tempid(partition),
             :'element.map/key' => Translation.from_ruby(key),
             element_value_attribute(value) => element_value(value)
            }
          }
        end

      end

      def array_to_datoms(data, partition=:'db.part/user')
        if data.empty?
          :'ref.vector/empty'
        else
          data.each_with_index.map { |value, index|
            {:'db/id' => Datomizer::Database.tempid(partition),
             :'element.vector/index' => index,
             element_value_attribute(value) => element_value(value)
            }
          }
        end
      end

      def entity_to_data(entity)
        Hash[entity.to_h.map { |k, v|
          [k, decode_elements(entity, k, v)]
        }]
      end

      def decode_elements(entity, key, elements)
        elements.is_a?(Set) or return elements
        elements == Set.new([:'ref.vector/empty']) and return []
        elements == Set.new([:'ref.map/empty']) and return {}

        case Datomizer::Marshalling.ref_type(entity, key)
          when :'ref/map', :'ref.type/map'
            Hash[elements.map { |pair| decode(entity, pair) }]
          when :'ref/vector', :'ref.type/vector'
            elements.map { |item| decode(entity, item) }.sort_by(&:first).map(&:last)
          else
            elements
        end
      end

      def decode(entity, element)
        element.is_a?(Hash) or return element

        key = element[:'element.map/key'] || element[:'element.vector/index']
        key or return element

        value_attribute = element.keys.detect { |k| k.to_s.start_with?('element.value/') }
        value = element[value_attribute]

        [key, decode_elements(entity, value_attribute, value)]
      end

      def element_value_attribute(value)
        type = case value
          when String
            'string'
          when Array
            'vector'
          when Hash
            'map'
          when Integer
            'long'
          #TODO: add more
          else
            raise ArgumentError, "Datomization not supported for type #{value.class.name}"
        end
        :"element.value/#{type}"
      end

      def element_value(value)
        case value
          when Hash, Array
            collection_to_datoms(value)
          when String, Integer
            value
          else
            raise ArgumentError, "Datomization not supported for type #{value.class.name}"
        end
      end

      REF_SCHEMA = <<-EDN_ERB

        [[:db/add #db/id[:db.part/user] :db/ident :ref/vector]
         [:db/add #db/id[:db.part/user] :db/ident :ref/map]
         [:db/add #db/id[:db.part/user] :db/ident :ref/value]
         [:db/add #db/id[:db.part/user] :db/ident :ref.map/empty]
         [:db/add #db/id[:db.part/user] :db/ident :ref.vector/empty]

         {:db/id #db/id[:db.part/db]
          :db/ident :ref/type
          :db/valueType :db.type/keyword
          :db/cardinality :db.cardinality/one
          :db/isComponent true
          :db/doc "T of entity pointed to by this ref."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.map/key
          :db/valueType :db.type/keyword
          :db/cardinality :db.cardinality/one
          :db/doc "Map entry key."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.vector/index
          :db/valueType :db.type/long
          :db/cardinality :db.cardinality/one
          :db/doc "Vector index"
          :db.install/_attribute :db.part/db}
        ]
      EDN_ERB

      ELEMENT_SCHEMA = <<-EDN
        [{:db/id #db/id[:db.part/db]
          :db/ident :element.value/bigdec
          :db/valueType :db.type/bigdec
          :db/cardinality :db.cardinality/one
          :db/doc "BigDecimal value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/bigint
          :db/valueType :db.type/bigint
          :db/cardinality :db.cardinality/one
          :db/doc "BigInteger value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/boolean
          :db/valueType :db.type/boolean
          :db/cardinality :db.cardinality/one
          :db/doc "Boolean value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/bytes
          :db/valueType :db.type/bytes
          :db/cardinality :db.cardinality/one
          :db/doc "Bytes value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/double
          :db/valueType :db.type/double
          :db/cardinality :db.cardinality/one
          :db/doc "Double value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/float
          :db/valueType :db.type/float
          :db/cardinality :db.cardinality/one
          :db/doc "Float value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/fn
          :db/valueType :db.type/fn
          :db/cardinality :db.cardinality/one
          :db/doc "Function value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/instant
          :db/valueType :db.type/instant
          :db/cardinality :db.cardinality/one
          :db/doc "Instant value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/keyword
          :db/valueType :db.type/keyword
          :db/cardinality :db.cardinality/one
          :db/doc "Keyword value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/long
          :db/valueType :db.type/long
          :db/cardinality :db.cardinality/one
          :db/doc "Long value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/ref
          :db/valueType :db.type/ref
          :db/cardinality :db.cardinality/one
          :db/doc "Ref value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/string
          :db/valueType :db.type/string
          :db/cardinality :db.cardinality/one
          :db/doc "String value of vector, map, or value element."
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/map
          :db/valueType :db.type/ref
          :db/cardinality :db.cardinality/many
          :db/doc "Map value of vector, map, or value element."
          :db/isComponent true
          :ref/type :ref.type/map
          :db.install/_attribute :db.part/db}

         {:db/id #db/id[:db.part/db]
          :db/ident :element.value/vector
          :db/valueType :db.type/ref
          :db/cardinality :db.cardinality/many
          :db/doc "Vector value of vector, map, or value element."
          :db/isComponent true
          :ref/type :ref.type/vector
          :db.install/_attribute :db.part/db}
        ]
      EDN

      #TODO: stop assuming db.part/user everywhere.
      def install_schema(database)
        database.transact(REF_SCHEMA)
        database.transact(ELEMENT_SCHEMA)
      end

    end
  end
end
