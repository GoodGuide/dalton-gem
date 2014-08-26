java_import "clojure.lang.PersistentHashSet"
java_import "clojure.lang.Keyword"

module Zweikopf
  module Primitive
    def self.is_primitive_type?(obj) # monkey patch to remove DateTime from list of primitives and allow them to be converted. :-/
      [String, Fixnum, Integer, Float, TrueClass, FalseClass].include?(obj.class)
    end
  end

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
          when Java::JavaUtil::ArrayList
            value.map { |x| from_clj(x) }
          when Java::Datomic::Entity, Java::DatomicQuery::EntityMap
            Dalton::Entity.new(value)
          when Java::JavaUtil::Date
            Time.at(value.getTime / 1000).to_datetime
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
          when DateTime
            Java::JavaUtil::Date.new(value.to_time.to_i * 1000)
          else
            value
        end
      end
    end

  end
end
