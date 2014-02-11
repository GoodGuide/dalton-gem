module Datomizer
  module Datomization

    def datomizer_schema
      Utility.read_edn(File.read(File.expand_path("../../../clojure/resources/datomizer-schema.edn", __FILE__)))
    end

    def datomizer_functions
      Utility.read_edn(File.read(File.expand_path("../../../clojure/resources/datomizer-functions.edn", __FILE__)))
    end

    def install_datomization_schema
      datomizer_schema.each { |definition| transact([definition]) }
      datomizer_functions.each { |definition| transact([definition]) }
    end

    def datomize(data)
      result = transact([[:'dmzr/datomize', data]])
      result.resolve_tempid(data[:'db/id'])
    end

    def undatomize(id)
      e = entity(id)
      Utility.require_clojure('datomizer.datomize.decode')
      clojure_data = Utility.run_clojure("datomizer.datomize.decode/undatomize", e.datomic_entity)
      Translation.from_clj(clojure_data)
    end

  end
end
