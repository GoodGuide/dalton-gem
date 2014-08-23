java_import "clojure.lang.Keyword"
java_import "datomic.Peer"

module Dalton
  class Connection

    include Dalton::Datomization

    def initialize(uri)
      @uri = uri
    end

    def self.connect(uri)
      Peer.createDatabase(uri)
      database = new(uri)
      database.connect
      return database
    end

    attr_reader :uri, :datomic_connection, :db

    def create
      Peer.createDatabase(uri) or
        raise DatomicError, "Unable to create database at \"#{uri}\"."
    end

    def destroy
      Peer.deleteDatabase(uri) or
        raise DatomicError, "Unable to destroy database at \"#{uri}\"."
    end

    def connect
      @datomic_connection = Peer.connect(uri) or
        raise DatomicError, "Unable to connect to database at \"#{uri}\"."
      refresh
      true
    end

    def db=(new_db)
      @db = new_db.is_a?(Database) ? new_db : Database.new(new_db)
    end

    def refresh
      self.db = @datomic_connection.db
      db
    end

    def transact(datoms)
      data = self.class.convert_datoms(datoms)
      # STDERR.puts "data=#{data.to_edn}"
      result = TransactionResult.new(@datomic_connection.transact(data).get)
      self.db = result.db_after
      Translation.from_clj(result)
    rescue Java::JavaUtilConcurrent::ExecutionException => e
      cause = e.getCause
      if cause.respond_to?(:data)
        err_data = Translation.from_clj(cause.data)
        case err_data[:'db/error']
        when :'db.error/unique-conflict'
          raise UniqueConflict.parse(cause.getMessage)
        when :'db.error/wrong-type-for-attribute'
          raise TypeError.parse(cause.getMessage)
        end
      end

      raise DatomicError, "Transaction failed: #{e.getMessage}"
    end


    def retract(entity)
      entity_id = entity.is_a?(Entity) ? entity.id : entity
      transact([[:'db.fn/retractEntity', entity_id]])
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

    def self.tempid?(id)
      0 > case id
      when Numeric
        id
      when Java::DatomicDb::DbId
        id.get(Utility::kw('idx'))
      end
    end
  end
end
