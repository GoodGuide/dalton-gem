java_import "clojure.lang.Keyword"
java_import "clojure.lang.PersistentArrayMap"
java_import "clojure.lang.RT"

module Dalton
  module Utility

    module_function

    def run_clojure_function(namespaced_function, *arguments)
      namespace, function_name = namespaced_function.to_s.split('/', 2)
      namespace && function_name or
        raise ArgumentError, "Namespaced function required. Got: #{namespaced_function.inspect}"
      RT.var(namespace, function_name).fn.invoke(*arguments)
    end

    def run_database_function(db, function_ident, *arguments)
      function_entity = db.entity(Translation.from_ruby(function_ident))
      function_entity.fn.invoke(*arguments)
    end

    def require_clojure(namespace)
      require_function = RT.var("clojure.core", "require").fn
      require_function.invoke(Java::ClojureLang::Symbol.intern(namespace))
    end

    require_clojure('datomic.function')
    require_clojure('datomic.db')

    def read_edn(edn)
      readers = PersistentArrayMap.create({Keyword.intern('readers') => PersistentArrayMap.create({
          Java::ClojureLang::Symbol.intern('db/fn') => RT.var('datomic.function', 'construct'),
          Java::ClojureLang::Symbol.intern('db/id') => RT.var('datomic.db', 'id-literal')
      })})

      run_clojure_function("clojure.edn/read-string", readers, edn)
    end

    def rubify_edn(edn)
      Translation.from_clj(read_edn(edn))
    end

    def clojure_equal?(one, other)
      run_clojure_function('clojure.core/=', one, other)
    end

    def to_edn(clojure_data)
      run_clojure_function('clojure.core/pr-str', clojure_data)
    end

    def sym(s)
      s = s.to_s if s.is_a? Symbol
      Java::ClojureLang::Symbol.intern(s)
    end

    def gensym(s)
      run_clojure_function('clojure.core/gensym', sym(s))
    end

    def tempid(partition)
      Peer.tempid(kw(partition))
    end

    def gensym(s)
      run_clojure_function('clojure.core/gensym', sym(s))
    end

    def kw(k)
      k = k.to_s if k.is_a? Symbol
      k = k[1..-1] if k.start_with? ':'
      Java::ClojureLang::Keyword.intern(k)
    end

    def list(*items)
      Dalton::Utility.run_clojure_function("clojure.core/list*", items)
    end

    def with_meta(value, meta)
      Dalton::Utility.run_clojure_function("clojure.core/with-meta", value, meta)
    end

    def meta(value)
      Dalton::Utility.run_clojure_function("clojure.core/meta", value)
    end

    def tag(value, tag)
      with_meta(value, Dalton::Translation.from_ruby(tag: tag))
    end
  end
end

