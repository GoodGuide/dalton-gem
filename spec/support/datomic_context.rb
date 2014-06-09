require 'rspec/core/shared_context'

module DatomicContext
  extend RSpec::Core::SharedContext

  let(:uri) { 'datomic:mem://spec' }
  # let(:uri) { 'datomic:dev://localhost:4334/spec' }
  let(:d) { Dalton::Database.new(uri) }

  before do
    d.create
    d.connect
    d.refresh
  end

  after do
    d.destroy
  end
end
