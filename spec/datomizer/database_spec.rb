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
      d.transact([{
        :'db/id' => Datomizer::Database.tempid,
        attribute => value
      }])

      result = d.q([:find, :'?e', :where, [:'?e', attribute, value]])
      expect(result).to be_a(Set)
      expect(result.size).to eq(1)

      entity_id = result.first.first
      entity = d.entity(entity_id)

      expect(entity[attribute]).to eql(value)

    end
  end

end


