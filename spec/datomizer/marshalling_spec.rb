require 'spec_helper'

describe Datomizer::Marshalling do

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
      d.transact(Datomizer::Marshalling::SCHEMA)
      d.transact([{:'db/id' => Datomizer::Database.tempid(':db.part/db'),
                   :'db/ident' => :'test/stuff',
                   :'db/valueType' => :'db.type/ref',
                   :'db/cardinality' => :'db.cardinality/one',
                   :'db/doc' => "A reference attribute for testing marshalling",
                   :'db/isComponent' => true,
                   :'db.install/_attribute' => :'db.part/db',
                  }])
    end

    shared_examples_for "a round-trip to/from the database" do
      it "should store and retrieve the value" do
        datoms = Datomizer::Marshalling.collection_to_datoms(value)

        d.transact([{:'db/id' => Datomizer::Database.tempid,
                     :'test/stuff' => datoms
                    }])

        entities = d.retrieve([:find, :'?e', :where, [:'?e', :'test/stuff']])
        expect(entities.size).to eq(1)
        entity = entities.first

        data = Datomizer::Marshalling.entity_to_data(entity)
        expect(data[:'test/stuff']).to eq(value)
      end

    end

    context "with simple map values" do
      let(:value) {{:a => 'fnord'}}

      it_should_behave_like "a round-trip to/from the database"
    end

    context "with nested map values" do
      let(:value) {{:a => {:b => 'fnord'}}}

      it_should_behave_like "a round-trip to/from the database"
    end

    context "with array values"

    context "with nested data structure values"

  end
end
