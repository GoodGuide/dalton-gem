java_import "datomic.Peer"

module Dalton
  class TransactionResult
    def initialize(result_map)
      @result_map = result_map
    end

    def db_before
      Dalton::Database.new(@result_map.get(Java::Datomic::Connection.DB_BEFORE))
    end

    def db_after
      Dalton::Database.new(@result_map.get(Java::Datomic::Connection.DB_AFTER))
    end

    def tx_data
      Translation.from_clj(@result_map.get(Java::Datomic::Connection.TX_DATA))
    end

    def raw_tempids
      @result_map.get(Java::Datomic::Connection.TEMPIDS)
    end

    def tempids
      Translation.from_clj(raw_tempids)
    end

    def resolve_tempid(tempid)
      Peer.resolve_tempid(db_after.datomic_db, raw_tempids, tempid)
    end
  end
end
