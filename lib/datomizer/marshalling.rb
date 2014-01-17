require 'erb'
module Datomizer
  module Marshalling

    module_function

    def collection_to_datoms(data, partition=:'db.part/user')
      case data
        when Hash
          hash_to_datoms(data, partition)
        when Array
          array_to_datoms(data, partition)
        else
          raise ArgumentError, "Marshalling not supported for type #{value.class.name}"
      end
    end

    def hash_to_datoms(data, partition=:'db.part/user')
      data.map { |key, value|
        {:'db/id' => Datomizer::Database.tempid(partition),
         :'element.map/key' => Translation.from_ruby(key),
         element_value_attribute(value) => element_value(value)
        }
      }
    end

    def array_to_datoms(data, partition=:'db.part/user')
      data.each_with_index.map { |value, index|
        {:'db/id' => Datomizer::Database.tempid(partition),
         :'element.vector/index' => index,
         element_value_attribute(value) => element_value(value)
        }
      }
    end

    def entity_to_data(entity)
      entity_hash_to_data(entity.to_h)
    end

    def entity_hash_to_data(entity_hash)
      Hash[entity_hash.map { |k, v|
        if v.is_a?(Set)
          [k, decode_elements(v)]
        else
          [k, v]
        end
      }]
    end

    def decode_elements(elements)
      elements.is_a?(Set) or return elements
      if elements.first && elements.first.has_key?(:'element.map/key')
        Hash[elements.map { |pair| decode(pair) }]
      elsif elements.first && elements.first.has_key?(:'element.vector/index')
        elements.map{|item| decode(item)}.sort_by(&:first).map(&:last)
      else
        elements
      end
    end

    def decode(element)
      element.is_a?(Hash) or return element
      key = element[:'element.map/key']  || element[:'element.vector/index']
      key or return element

      #TODO: make this less stupid.
      value =
        element[:'element.value/bigdec'] ||
        element[:'element.value/bigint'] ||
        element[:'element.value/boolean'] ||
        element[:'element.value/bytes'] ||
        element[:'element.value/double'] ||
        element[:'element.value/float'] ||
        element[:'element.value/fn'] ||
        element[:'element.value/instant'] ||
        element[:'element.value/keyword'] ||
        element[:'element.value/long'] ||
        element[:'element.value/ref'] ||
        element[:'element.value/string'] ||
        element[:'element.value/uri'] ||
        element[:'element.value/uuid'] ||
        element[:'element.value/map'] ||
        element[:'element.value/vector']

      [key, decode_elements(value)]
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
          raise ArgumentError, "Marshalling not supported for type #{value.class.name}"
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
          raise ArgumentError, "Marshalling not supported for type #{value.class.name}"
      end
    end

    REF_SCHEMA = <<-EDN_ERB

      [[:db/add #db/id[:db.part/user] :db/ident :ref/vector]
       [:db/add #db/id[:db.part/user] :db/ident :ref/map]
       [:db/add #db/id[:db.part/user] :db/ident :ref/value]

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
        :ref/type :ref.type/map
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :element.value/vector
        :db/valueType :db.type/ref
        :db/cardinality :db.cardinality/many
        :db/doc "Vector value of vector, map, or value element."
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
