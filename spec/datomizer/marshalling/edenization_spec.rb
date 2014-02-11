require 'spec_helper'

describe Datomizer::Marshalling::Edenization do

  let(:uri) { 'datomic:mem://spec' }
  let(:d) { Datomizer::Database.new(uri) }

  before do
    d.create
    d.connect
    d.refresh
  end

  after do
    d.destroy
  end

  describe 'data structure handling' do
    let(:attribute) {:'test/edn'}
    before do
      Datomizer::Marshalling.install_schema(d)
      d.transact([{:'db/id' => Datomizer::Database.tempid(':db.part/db'),
                    :'db/ident' => attribute,
                    :'db/valueType' => :'db.type/string',
                    :'db/cardinality' => :'db.cardinality/one',
                    :'db/doc' => "An EDN string field for edenization testing.",
                    :'dmzr.ref/type' => :'dmzr.ref.type/edn',
                    :'db.install/_attribute' => :'db.part/db'}])
    end

    shared_examples_for "a round-trip to/from the database" do
      it "should store and retrieve the value" do
        id = Datomizer::Database.tempid
        original_data = {:'db/id' => id, attribute => value}
        real_id = d.edenize(original_data)
        round_tripped_data = d.unedenize(real_id)
        expect(round_tripped_data[attribute]).to eq(value)
      end
    end

    context "with an empty map" do
      let(:value) { {} }

      it_should_behave_like "a round-trip to/from the database"
    end

    context "with a single map element" do
      let(:value) { {:a => 'grue'} }

      it_should_behave_like "a round-trip to/from the database"
    end

    context "with multiple map elements" do
      let(:value) { {:a => 'grue', :b => 'wumpus'} }

      it_should_behave_like "a round-trip to/from the database"
    end

    context "with a nested map" do
      let(:value) { {:a => {:b => 'fnord'}} }

      it_should_behave_like "a round-trip to/from the database"
    end

    context "with an empty array" do
      let(:value) {[]}

      it_should_behave_like "a round-trip to/from the database"
    end

    context "with array values" do
      let(:value) {['a', 'b', 'c']}

      it_should_behave_like "a round-trip to/from the database"
    end

    context "with nested data structure values" do
      let(:value) {[0, 1, 'a', 'b', 'c', ['x', 'y', 'z']]}

      it_should_behave_like "a round-trip to/from the database"
    end

  end
end
