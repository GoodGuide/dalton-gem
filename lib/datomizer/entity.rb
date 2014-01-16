module Datomizer
  class Entity

    def initialize(datomic_entity)
      @datomic_entity = datomic_entity
    end

    attr_accessor :datomic_entity

    def get(key)
      value = datomic_entity.get(Zweikopf::Keyword.from_ruby(key))
      case value
        when Java::DatomicQuery::EntityMap
          Datomic::Entity.new(value)
        when Java::ClojureLang::PersistentHashSet
          Set.new(value.map {|x| x.is_a?(Java::DatomicQuery::EntityMap) ? Datomic::Entity.new(x) : x })
        else
          value
      end
    end

    alias_method :[], :get

    def keys
      datomic_entity.keySet.to_a
    end

    def id
      get(:'db/id')
    end

    def ==(other)
      other.instance_of?(self.class) && Utility.clojure_equal?(datomic_entity, other.datomic_entity)
    end
  end
end
