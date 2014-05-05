java_import "clojure.lang.PersistentHashSet"
java_import "clojure.lang.Keyword"

java_import "clojure.lang.Keyword"

require 'zweikopf'

module Zweikopf
  module Keyword
    # Monkey patch special handling for datalog variables
    def self.from_ruby(keyword)
      if keyword.to_s =~ /^[?$]/
        Java::ClojureLang::Symbol.intern(keyword.to_s)
      else
        ::Keyword.intern(keyword.to_s)
      end
    end
  end
end

module Dalton
  module Translation

    module_function

    #TODO: Fork Zweikopf, add Set handling, submit pull request

    def from_clj(object)
      Zweikopf::Transformer.from_clj(object) do |value|
        case value
          when Java::ClojureLang::Symbol
            value.to_s.to_sym
          when Java::JavaUtil::Set
            Set.new(value.map{|x| from_clj(x)})
          when Java::Datomic::Entity
            Dalton::Entity.new(value)
          else
            value
        end
      end
    end

    def from_ruby(object)
      Zweikopf::Transformer.from_ruby(object) do |value|
        case value
          when ::Set
            PersistentHashSet.create(value.map{|x| from_ruby(x)})
          when Dalton::Entity
            value.datomic_entity
          else
            value
        end
      end
    end

  end
end
