require 'spec_helper'

describe Datomizer::Marshalling::Datomization do

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
    before do
      Datomizer::Marshalling::Serialization.install_schema(d)
      d.transact([{:'db/id' => Datomizer::Database.tempid(':db.part/db'),
                    :'db/ident' => :'test/edn-value',
                    :'db/valueType' => :'db.type/string',
                    :'db/cardinality' => :'db.cardinality/one',
                    :'db/doc' => "A reference attribute for testing serialization",
                    :'db/isComponent' => true,
                    :'ref/type' => :'ref/edn',
                    :'db.install/_attribute' => :'db.part/db',
                   }])
    end

    shared_examples_for "a round-trip to/from the database" do
      it "should store and retrieve the value" do
        collection_edn = Datomizer::Marshalling::Serialization.collection_to_edn(value)

        d.transact([{:'db/id' => Datomizer::Database.tempid,
                     :'test/edn-value' => collection_edn
                    }])

        entities = d.retrieve([:find, :'?e', :where, [:'?e', :'test/edn-value']])
        expect(entities.size).to eq(1)
        entity = entities.first

        data = Datomizer::Marshalling::Serialization.entity_to_data(entity)

        expect(data[:'test/edn-value']).to eq(value)
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
