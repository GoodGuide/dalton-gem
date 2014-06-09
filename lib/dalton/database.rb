java_import "clojure.lang.Keyword"
java_import "datomic.Peer"

require_relative 'datomization'

module Dalton
  class Database

    include Dalton::Datomization

    def initialize(uri)
      @uri = uri
    end

    def self.connect(uri)
      d = new(uri)
      d.create rescue nil
      d.connect
      d
    end

    attr_reader :uri, :conn, :db

    def create
      Peer.createDatabase(uri) or
        raise DatomicError, "Unable to create database at \"#{uri}\"."
    end

    def destroy
      Peer.deleteDatabase(uri) or
        raise DatomicError, "Unable to destroy database at \"#{uri}\"."
    end

    def connect
      @conn = Peer.connect(uri) or
        raise DatomicError, "Unable to connect to database at \"#{uri}\"."
      refresh
    end

    def refresh
      @db = @conn.db
    end

    def transact(datoms)
      data = self.class.convert_datoms(datoms)
      # STDERR.puts "data=#{data.to_edn}"
      result = TransactionResult.new(@conn.transact(data).get)
      @db = result.db_after
      Translation.from_clj(result)
    rescue Java::JavaUtilConcurrent::ExecutionException => e
      raise DatomicError, "Transaction failed: #{e.getMessage}"
    end

    def q(query, *args)
      translated_query = Translation.from_ruby(query)
      # STDERR.puts "translated_query=#{translated_query.to_edn}"
      result = Peer.q(translated_query, db, *args)
      Translation.from_clj(result)
    rescue Java::JavaUtilConcurrent::ExecutionException => e
      raise DatomicError, "Query failed: #{e.getMessage}"
    end

    def entity(entity_id)
      entity = db.entity(Translation.from_ruby(entity_id))
      Translation.from_clj(entity)
    rescue Java::JavaUtilConcurrent::ExecutionException => e
      raise DatomicError, "Entity retrieval failed: #{e.getMessage}"
    end

    def retrieve(query, *inputs)
      q(query, *inputs).lazy.map { |result| entity(result.first) }
    end

    def retract(entity)
      entity_id = entity.is_a?(Entity) ? entity.id : entity
      transact([[:'db.fn/retractEntity', entity_id]])
    end

    def attribute(id)
      Attribute.new(db.attribute(Translation.from_ruby(id)))
    end

    def basis_t
      db.basisT
    end

    def self.convert_datoms(datoms)
      case datoms
        when Array
          Translation.from_ruby(datoms)
        when String
          Utility.read_edn(datoms)
        else
          raise ArgumentError, 'datoms must be an Array or a String containing EDN.'
      end
    end

    def self.tempid(partition=:'db.part/user', id=nil)
      partition = Keyword.intern(partition.to_s.sub(/^:/, ''))
      if id
        Peer.tempid(partition, id)
      else
        Peer.tempid(partition)
      end
    end
  end
end
