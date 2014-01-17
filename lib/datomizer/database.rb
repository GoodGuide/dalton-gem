module Datomizer
  class Database

    def initialize(uri)
      @uri = uri
    end

    def self.connect(uri)
      d = new(uri)
      d.create rescue nil
      d.connect
      d
    end

    attr_reader :uri, :dbc, :db

    def create
      Java::Datomic::Peer.createDatabase(uri) or
        raise "Unable to create database at \"#{uri}\"."
    end

    def destroy
      Java::Datomic::Peer.deleteDatabase(uri) or
        raise "Unable to destroy database at \"#{uri}\"."
    end

    def connect
      @dbc = Java::Datomic::Peer.connect(uri) or
        raise "Unable to connect to database at \"#{uri}\"."
      refresh
    end

    def refresh
      @db = @dbc.db
    end

    def transact(datoms)
      data = self.class.convert_datoms(datoms)
      result = TransactionResult.new(@dbc.transact(data).get)
      @db = result.db_after
      result
    rescue Java::JavaUtilConcurrent::ExecutionException => e
      raise "Transaction failed: #{e.getMessage}"
    end

    def q(query)
      result = Java::Datomic::Peer.q(Translation.from_ruby(query), db)
      Translation.from_clj(result)
    rescue Java::JavaUtilConcurrent::ExecutionException => e
      raise "Query failed: #{e.getMessage}"
    end

    def entity(entity_id)
      entity = db.entity(entity_id)
      Translation.from_clj(entity)
    rescue Java::JavaUtilConcurrent::ExecutionException => e
      raise "Entity retrieval failed: #{e.getMessage}"
    end

    def retrieve(query)
      q(query).map { |result| entity(result.first) }
    end

    def retract(entity)
      entity_id = entity.is_a?(Entity) ? entity.id : entity
      transact(Translation.from_ruby([[:'db.fn/retractEntity', entity_id]]))
    end

    def self.convert_datoms(datoms)
      case datoms
        when Array
          Translation.from_ruby(datoms)
        when String
          Utility.read_edn(datoms)
        else
          datoms
      end
    end

    def self.tempid(partition=:'db.part/user', id=nil)
      partition = Java::ClojureLang::Keyword.intern(partition.to_s.sub(/^:/, ''))
      if id
        Java::Datomic::Peer.tempid(partition, id)
      else
        Java::Datomic::Peer.tempid(partition)
      end
    end
  end
end
