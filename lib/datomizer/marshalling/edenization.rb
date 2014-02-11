module Datomizer
  module Marshalling
    module Edenization

      def edenize(data)
        result = transact([[:'dmzr/edenize', data]])
        result.resolve_tempid(data[:'db/id'])
      end

      def unedenize(id)
        e = entity(id)
        clojure_data = Utility.run_database_function(self, :'dmzr/unedenize', e.datomic_entity)
        Translation.from_clj(clojure_data)
      end

    end
  end
end
