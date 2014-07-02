java_import "clojure.lang.Keyword"
java_import "datomic.Peer"

require_relative 'datomization'

module Dalton
  class UniqueConflict < DatomicError
    # TODO: [jneen] this is terrible, but error handling is not implemented at the moment.
    # eventually all this data should be accessible via (ex-data e).
    MESSAGE_RE =
      %r(^:db[.]error/unique-conflict Unique conflict: :([a-z./-]+), value: (.*?) already held by: (\d+) asserted for: (\d+)$)o

    def self.parse(message)
      message =~ MESSAGE_RE
      raise ArgumentError, "invalid format: #{message.inspect}" unless $~
      new(
        attribute: $1.to_sym,
        value: $2,
        existing_id: Integer($3),
        new_id: Integer($4),
      )
    end

    attr_reader :attribute, :value, :existing_id, :new_id
    def initialize(opts={})
      @attribute = opts.fetch(:attribute)
      @value = opts.fetch(:value)
      @existing_id = opts.fetch(:existing_id)
      @new_id = opts.fetch(:new_id)
    end

    def message
      "Unique conflict: tried to assign duplicate #@attribute to #@new_id, already held by #@existing_id. value: #@value"
    end
  end

  class TypeError < DatomicError
    MESSAGE_RE = %r(^:db[.]error/wrong-type-for-attribute Value (.*?) is not a valid :(\w+) for attribute :([a-z./-]+)$)

    def self.parse(message)
      message =~ MESSAGE_RE
      raise ArgumentError, "invalid format: #{message.inspect}" unless $~
      new(
        value: $1,
        type: $2.to_sym,
        attribute: $3.to_sym
      )
    end

    attr_reader :value, :type, :attribute
    def initialize(opts={})
      @value = opts.fetch(:value)
      @type = opts.fetch(:type)
      @attribute = opts.fetch(:attribute)
    end

    def message
      "Type error: tried to set #@attribute as #@value, expected type #@type"
    end
  end

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
  end
end
