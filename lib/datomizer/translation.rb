java_import "clojure.lang.IPersistentSet"
java_import "clojure.lang.PersistentHashSet"
java_import "clojure.lang.Keyword"

module Datomizer
  module Translation

    module_function

    #TODO: Fork Zweikopf, add Set handling, submit pull request

    def from_clj(object)
      Zweikopf::Transformer.from_clj(object) do |value|
        case value
          when IPersistentSet
            Set.new(value.map{|x| from_clj(x)})
          when Java::Datomic::Entity
            Datomizer::Entity.new(value)
          else
            value
        end
      end
    end

    def from_ruby(object)
      Zweikopf::Transformer.from_ruby(object) do |value|
        case value
          when Set
            PersistentHashSet.create(value.map{|x| from_ruby(x)})
          when Datomizer::Entity
            value.datomic_entity
          else
            value
        end
      end
    end

  end
end
