module Datomizer
  module Marshalling

    # Based on work published by Chas Emerick here: https://gist.github.com/cemerick/3e615a4d42b88ccefdb4

    SCHEMA = <<-EDN
      [[:db/add #db/id[:db.part/user -10] :db/ident :coll/list]
       [:db/add #db/id[:db.part/user -11] :db/ident :coll/map]

       {:db/id #db/id[:db.part/db]
        :db/ident :coll/type
        :db/valueType :db.type/keyword
        :db/cardinality :db.cardinality/one
        :db/isComponent true
        :db/doc "Keyword indicating type of collection represented by the attributes rooted at this entity, either :coll/list or :coll/map."
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :list/str-val
        :db/valueType :db.type/string
        :db/cardinality :db.cardinality/one
        :db/isComponent true
        :db/doc "String cons cell value."
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :list/ref-val
        :db/valueType :db.type/ref
        :db/cardinality :db.cardinality/one
        :db/isComponent true
        :db/doc "Non-scalar cons cell value."
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :list/next
        :db/valueType :db.type/ref
        :db/cardinality :db.cardinality/one
        :db/isComponent true
        :db/doc "Ref to next cons."
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :map/entry
        :db/valueType :db.type/ref
        :db/cardinality :db.cardinality/many
        :db/isComponent true
        :db/doc "Refs to map entries."
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :map/key
        :db/valueType :db.type/keyword
        :db/cardinality :db.cardinality/one
        :db/doc "Key(word) of a map entry"
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :map/str-val
        :db/valueType :db.type/string
        :db/cardinality :db.cardinality/one
        :db/doc "String val of a map entry"
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :map/ref-val
        :db/valueType :db.type/ref
        :db/cardinality :db.cardinality/one
        :db/isComponent true
        :db/doc "Non-scalar map entry value."
        :db.install/_attribute :db.part/db}]
    EDN

    module_function

    def collection_to_datoms(data, partition=:'db.part/user')
      {:'db/id' => Datomizer::Database.tempid(partition),
       :'coll/type' => :'coll/map',
       :'map/entry' => data.map { |key, value|
         {:'db/id' => Datomizer::Database.tempid(partition),
          :'map/key' => Translation.from_ruby(key),
           entry_value_type(value) => entry_value(value)
         }}}
    end

    def entity_to_data(entity)
      entity_hash_to_data(entity.to_h)
    end

    def entity_hash_to_data(entity_hash)
      Hash[entity_hash.map{|k,v| [k, decode(v)]}]
    end

    def decode(value)
      value.is_a?(Hash) && value[:'coll/type'] == :'coll/map' or return value
      Hash[value[:'map/entry'].map{|entry| [entry[:'map/key'], decode(entry[:'map/str-val'] || entry[:'map/ref-val'])]}]
    end

    def entry_value_type(value)
      case value
        when Hash
          :'map/ref-val'
        when String
          :'map/str-val'
        else
          raise ArgumentError, "Marshalling not supported for type #{value.class.name}"
      end
    end

    def entry_value(value)
      case value
        when Hash
          collection_to_datoms(value)
        when String
          value
        else
          raise ArgumentError, "Marshalling not supported for type #{value.class.name}"
      end
    end

  end
end
