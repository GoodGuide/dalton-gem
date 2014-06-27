require 'rspec/core/shared_context'

module DatomicContext
  extend RSpec::Core::SharedContext

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
