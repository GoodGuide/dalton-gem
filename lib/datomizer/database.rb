module Datomizer
  class Database

    def initialize(uri)
      @uri = uri
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
    end

    def refresh
      @db = @dbc.db
    end

    def transact(datoms)
      datoms = Zweikopf::Transformer.from_ruby(datoms) if datoms.is_a?(Array)
      result = @dbc.transact(datoms).get
      @db = result.get(Java::ClojureLang::Keyword.intern('db-after'))
      result # TODO: Wrap result to make it more usable from ruby.
    rescue Java::JavaUtilConcurrent::ExecutionException => e
      raise "Transaction failed: #{e.getMessage}"
    end

    def q(query)
      raw_result = Java::Datomic::Peer.q(self.class.convert_query(query), db)
      self.class.convert_result(raw_result)
    end

    def entity(entity_id)
      raw_entity = db.entity(entity_id)
      self.class.convert_entity(raw_entity)
    end

    def self.convert_query(q)
      Zweikopf::Transformer.from_ruby(q)
    end

    def self.convert_result(result)
      Set.new(result.map(&:to_a))
    end

    def self.convert_entity(e)
      ::Datomizer::Entity.new(e)
    end

    def self.tempid(id=1)
      Java::Datomic::Peer.tempid(':db.part/user', id * -1)
    end
  end
end
