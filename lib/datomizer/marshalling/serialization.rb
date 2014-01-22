require 'erb'
module Datomizer
  module Marshalling
    module Serialization

      module_function

      def collection_to_edn(data)
        Translation.from_ruby(data).to_edn
      end

      def entity_to_data(entity)
        Hash[entity.to_h.map { |k, v|
          [k, decode_value(entity, k, v)]
        }]
      end

      def decode_value(entity, key, value)
        decoded_value = if value.is_a?(String) && Datomizer::Marshalling.ref_type(entity, key) == :'ref/edn'
          Utility.rubify_edn(value)
        else
          value
        end
        Translation.from_clj(decoded_value)
      end

      REF_SCHEMA = <<-EDN_ERB
        [[:db/add #db/id[:db.part/user] :db/ident :ref/edn]
         {:db/id #db/id[:db.part/db]
          :db/ident :ref/type
          :db/valueType :db.type/keyword
          :db/cardinality :db.cardinality/one
          :db/isComponent true
          :db/doc "Type of entity pointed to by this ref."
          :db.install/_attribute :db.part/db}
        ]
      EDN_ERB

      def install_schema(database)
        database.transact(REF_SCHEMA)
      end

    end
  end
end
