module DatomicContext
  def self.included(base)
    base.module_eval do
      let(:uri) { 'datomic:mem://spec' }
      # let(:uri) { 'datomic:dev://localhost:4334/spec' }
      let(:conn) { Dalton::Connection.new(uri) }
      let(:db) { conn.refresh }

      before do
        conn.create
        conn.connect
      end

      after do
        conn.destroy
      end
    end
  end
end
