module Datomizer
  module Marshalling

    autoload :Datomization, File.join(File.dirname(__FILE__), 'marshalling', 'datomization')
    autoload :Serialization, File.join(File.dirname(__FILE__), 'marshalling', 'serialization')

    module_function

    def ref_type(entity, key)
      field = entity.db.entity(Keyword.intern(key.to_s))
      Translation.from_clj(field.get(Keyword.intern('ref/type')))
    end

    def datomizer_schema
      Datomizer::Utility.read_edn(File.read(File.expand_path("../../../clojure/resources/datomizer-schema.edn", __FILE__)))
    end

    def datomizer_functions
      Datomizer::Utility.read_edn(File.read(File.expand_path("../../../clojure/resources/datomizer-functions.edn", __FILE__)))
    end

    def install_schema(database)
      datomizer_schema.each {|definition| database.transact([definition])}
      datomizer_functions.each {|definition| database.transact([definition])}
    end
  end
end
