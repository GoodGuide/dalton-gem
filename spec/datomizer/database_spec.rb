require 'rspec'
require_relative('../../lib/datomizer')

describe Datomizer::Database do

  let(:uri){'datomic:mem://spec'}
  let(:d){Datomizer::Database.new(uri)}

  before do
    d.create
    d.connect
    d.refresh
  end

  after do
    d.destroy
  end

  describe "data storage, query, and retrieval" do
    let(:attribute) {:'db/doc'}
    let(:value) {'This is a test entity.'}

    it "should store and retrieve data" do
      transaction_result = d.transact([{
        :'db/id' => Datomizer::Database.tempid,
        attribute => value
      }])

      expect(transaction_result.db_before).to be_a(Java::Datomic::Database)
      expect(transaction_result.db_after).to be_a(Java::Datomic::Database)
      expect(transaction_result.tx_data).to be_a(Array)
      expect(transaction_result.tempids).to be_a(Hash)

      query_result = d.q([:find, :'?e', :where, [:'?e', attribute, value]])
      expect(query_result).to be_a(Set)
      expect(query_result.size).to eq(1)

      entity_id = query_result.first.first
      entity = d.entity(entity_id)

      expect(entity[attribute]).to eql(value)

    end
  end

end


