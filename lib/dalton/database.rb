module Dalton

  class Database

    include Dalton::Undatomization

    def initialize(datomic_database_value)
      @datomic_db = datomic_database_value
    end

    attr_reader :datomic_db

    def q(query, *args)
      translated_query = Translation.from_ruby(query)
      # STDERR.puts "translated_query=#{translated_query.to_edn}"
      result = Peer.q(translated_query, datomic_db, *args)
      Translation.from_clj(result)
    rescue Java::JavaUtilConcurrent::ExecutionException => e
      raise DatomicError, "Query failed: #{e.getMessage}"
    end

    def entity(entity_id)
      entity = datomic_db.entity(Translation.from_ruby(entity_id))
      Translation.from_clj(entity)
    rescue Java::JavaUtilConcurrent::ExecutionException => e
      raise DatomicError, "Entity retrieval failed: #{e.getMessage}"
    end

    def retrieve(query, *inputs)
      q(query, *inputs).lazy.map { |result| entity(result.first) }
    end

    def attribute(id)
      Attribute.new(datomic_db.attribute(Translation.from_ruby(id)))
    end

    def basis_t
      datomic_db.basisT
    end

    def ==(other)
      self.datomic_db == other.datomic_db
    end
  end
end
