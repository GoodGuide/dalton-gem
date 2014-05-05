module Dalton
  class Entity

    include Enumerable

    def initialize(datomic_entity)
      @datomic_entity = datomic_entity
    end

    attr_reader :datomic_entity

    def db
      datomic_entity.db
    end

    def get(key)
      Translation.from_clj(datomic_entity.get(Translation.from_ruby(key)))
    end

    alias_method :[], :get

    def touch
      @datomic_entity.touch
      self
    end

    def keys
      datomic_entity.keySet.map{|x| x.sub(/^:/, '').to_sym}.to_a
    end

    def id
      get(:'db/id')
    end

    def each
      if block_given?
        keys.each do |key|
          yield [key, get(key)]
        end
        self
      else
        Enumerator.new(self)
      end
    end
    alias_method :each_pair, :each

    def to_h
      Hash[map {|key, value|
        [key, decode(value)]
      }]
    end

    def ==(other)
      other.instance_of?(self.class) && Utility.clojure_equal?(datomic_entity, other.datomic_entity)
    end

    def decode(value)
      case value
        when Dalton::Entity
          value.to_h
        when Set
          Set.new(value.map{|x| decode(x)})
        else
          Translation.from_clj(value)
      end
    end

    DB_FUNCTION_KEY = ::Keyword.intern('db/fn')

    def fn
      @datomic_entity.get(DB_FUNCTION_KEY)
    end

  end
end
