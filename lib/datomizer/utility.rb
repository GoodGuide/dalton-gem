java_import "clojure.lang.Keyword"
java_import "clojure.lang.PersistentArrayMap"
java_import "clojure.lang.RT"

module Datomizer
  module Utility

    module_function

    def run_clojure(namespaced_function, *arguments)
      namespace, function = namespaced_function.to_s.split('/', 2)
      RT.var(namespace, function).fn.invoke(*arguments)
    end

    def run_database_function(db, function_ident, *arguments)
      function_entity = db.entity(Translation.from_ruby(function_ident))
      function_entity.fn.invoke(*arguments)
    end

    def require_clojure(namespace)
      require_function = RT.var("clojure.core", "require").fn
      require_function.invoke(Java::ClojureLang::Symbol.intern(namespace))
    end

    def read_edn(edn)
      require_clojure('datomic.function')
      require_clojure('datomic.db')

      readers = PersistentArrayMap.create({Keyword.intern('readers') => PersistentArrayMap.create({
          Java::ClojureLang::Symbol.intern('db/fn') => RT.var('datomic.function', 'construct'),
          Java::ClojureLang::Symbol.intern('db/id') => RT.var('datomic.db', 'id-literal')
      })})

      run_clojure("clojure.edn/read-string", readers, edn)
    end

    def rubify_edn(edn)
      Translation.from_clj(read_edn(edn))
    end

    def clojure_equal?(one, other)
      run_clojure('clojure.core/=', one, other)
    end

    def to_edn(clojure_data)
      run_clojure('clojure.core/pr-str', clojure_data)
    end

  end
end

