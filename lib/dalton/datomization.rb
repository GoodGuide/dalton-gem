require_relative 'utility'

module Dalton
  module Datomization

    Utility.require_clojure('goodguide.datomizer.datomize.setup')
    Utility.require_clojure('goodguide.datomizer.datomize.decode')

    def set_up_datomizer
      Utility.run_clojure_function('goodguide.datomizer.datomize.setup/load-datomizer-schema', datomic_connection)
      Utility.run_clojure_function('goodguide.datomizer.datomize.setup/load-datomizer-functions', datomic_connection)
    end

    def datomize(data)
      result = transact([[:'dmzr/datomize', data]])
      result.resolve_tempid(data[:'db/id'])
    end
  end

end
