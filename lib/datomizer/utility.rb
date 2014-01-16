module Datomizer
  module Utility

    module_function

    def run_clojure(namespaced_function, *arguments)
      namespace, function = namespaced_function.split('/', 2)
      Java::ClojureLang::RT.var(namespace, function).fn().invoke(*arguments)
    end

    def require_clojure(namespace)
      require_function = Java::ClojureLang::RT.var("clojure.core", "require").fn()
      require_function.invoke(Java::ClojureLang::Symbol.intern(namespace))
    end

    def read_edn(edn)
      require_clojure('datomic.function')
      require_clojure('datomic.db')

      readers = Java::ClojureLang::PersistentArrayMap.create({Java::ClojureLang::Keyword.intern('readers') => Java::ClojureLang::PersistentArrayMap.create({
        Java::ClojureLang::Symbol.intern('db/fn') => Java::ClojureLang::RT.var('datomic.function', 'construct'),
        Java::ClojureLang::Symbol.intern('db/id') => Java::ClojureLang::RT.var('datomic.db', 'id-literal')
      })})

      run_clojure("clojure.edn/read-string", readers, edn)
    end

    def rubify_edn(edn)
      data = read_edn(edn)
      Zweikopf::Transformer.from_clj(data) do |x|
        x.is_a?(Java::ClojureLang::Symbol) ? x.to_s.to_sym : x
      end
    end

  end
end
