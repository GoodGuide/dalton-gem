module Dalton

  module Undatomization
    def undatomize(id)
      e = entity(id)
      clojure_data = Utility.run_clojure_function("goodguide.datomizer.datomize.decode/undatomize", e.datomic_entity)
      Translation.from_clj(clojure_data)
    end
  end

end
