module Datomizer
  module Marshalling

    module_function

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
