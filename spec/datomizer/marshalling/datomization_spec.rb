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
      Datomizer::Marshalling::Datomization.install_schema(d)
      d.transact([
                   {:'db/id' => Datomizer::Database.tempid(':db.part/db'),
                    :'db/ident' => :'test/map',
                    :'db/valueType' => :'db.type/ref',
                    :'db/cardinality' => :'db.cardinality/many',
                    :'db/doc' => "A reference attribute for testing datomization",
                    :'db/isComponent' => true,
                    :'ref/type' => :'ref.type/map',
                    :'db.install/_attribute' => :'db.part/db',
                   },
                   {:'db/id' => Datomizer::Database.tempid(':db.part/db'),
                    :'db/ident' => :'test/vector',
                    :'db/valueType' => :'db.type/ref',
                    :'db/cardinality' => :'db.cardinality/many',
                    :'db/doc' => "A reference attribute for testing datomization",
                    :'db/isComponent' => true,
                    :'ref/type' => :'ref.type/vector',
                    :'db.install/_attribute' => :'db.part/db',
                   },
                 ])
    end

    shared_examples_for "a round-trip to/from the database" do |attribute|
      it "should store and retrieve the value" do
        collection_datoms = Datomizer::Marshalling::Datomization.collection_to_datoms(value)

        d.transact([{:'db/id' => Datomizer::Database.tempid,
                     attribute => collection_datoms
                    }])

        entities = d.retrieve([:find, :'?e', :where, [:'?e', attribute]])
        expect(entities.size).to eq(1)
        entity = entities.first

        data = Datomizer::Marshalling::Datomization.entity_to_data(entity)

        expect(data[attribute]).to eq(value)
      end

    end

    context "with an empty map" do
      let(:value) { {} }

      it_should_behave_like "a round-trip to/from the database", :'test/map'
    end

    context "with a single map element" do
      let(:value) { {:a => 'grue'} }

      it_should_behave_like "a round-trip to/from the database", :'test/map'
    end

    context "with multiple map elements" do
      let(:value) { {:a => 'grue', :b => 'wumpus'} }

      it_should_behave_like "a round-trip to/from the database", :'test/map'
    end

    context "with a nested map" do
      let(:value) { {:a => {:b => 'fnord'}} }

      it_should_behave_like "a round-trip to/from the database", :'test/map'
    end

    context "with an empty array" do
      let(:value) {[]}

      it_should_behave_like "a round-trip to/from the database", :'test/vector'
    end

    context "with array values" do
      let(:value) {['a', 'b', 'c']}

      it_should_behave_like "a round-trip to/from the database", :'test/vector'
    end

    context "with nested data structure values" do
      let(:value) {[0, 1, 'a', 'b', 'c', ['x', 'y', 'z']]}

      it_should_behave_like "a round-trip to/from the database", :'test/vector'
    end

  end
end
