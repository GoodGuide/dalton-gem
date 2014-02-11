module Datomizer
  module Marshalling
    module Datomization

      def datomize(data)
        result = transact([[:'dmzr/datomize', data]])
        result.resolve_tempid(data[:'db/id'])
      end

      def undatomize(id)
        e = entity(id)
        clojure_data = Utility.run_database_function(self, :'dmzr/undatomize', e.datomic_entity)
        Translation.from_clj(clojure_data)
      end

    end
  end
end
