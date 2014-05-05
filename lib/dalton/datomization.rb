require_relative 'utility'

module Dalton
  module Datomization

    Utility.require_clojure('datomizer.datomize.setup')
    Utility.require_clojure('datomizer.datomize.decode')

    def set_up_datomizer
      Utility.run_clojure_function('datomizer.datomize.setup/load-datomizer-schema', conn)
      Utility.run_clojure_function('datomizer.datomize.setup/load-datomizer-functions', conn)
    end

    def datomize(data)
      result = transact([[:'dmzr/datomize', data]])
      result.resolve_tempid(data[:'db/id'])
    end

    def undatomize(id)
      e = entity(id)
      clojure_data = Utility.run_clojure_function("datomizer.datomize.decode/undatomize", e.datomic_entity)
      Translation.from_clj(clojure_data)
    end

  end
end
