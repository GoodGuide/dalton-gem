require 'spec_helper'

describe Datomizer::Entity do

  let(:uri) { 'datomic:mem://spec' }
  let(:d) { Datomizer::Database.new(uri) }

  before do
    d.create
    d.connect
    d.refresh
    d.transact([{:'db/id' => Datomizer::Database.tempid(':db.part/db'),
                 :'db/ident' => :'test/stuff',
                 :'db/valueType' => :'db.type/ref',
                 :'db/cardinality' => :'db.cardinality/one',
                 :'db/doc' => "A reference attribute for testing marshalling",
                 :'db/isComponent' => true,
                 :'db.install/_attribute' => :'db.part/db',
                }])
  end

  after do
    d.destroy
  end

  describe "#to_h" do
    before do
      d.transact([{:'db/id' => Datomizer::Database.tempid,
                    :'db/doc' => "foo",
                    :'test/stuff' => {:'db/id' => Datomizer::Database.tempid,
                                      :'db/doc' => "bar"}}
                 ])

    end

    let(:entity) { d.retrieve([:find, :'?e', :where, [:'?e', :'db/doc', 'foo']]).first }
    subject { entity.to_h }

    it "should translate the entity to a hash" do
      expect(subject).to eq({:"db/doc"=>"foo", :"test/stuff"=>{:"db/doc"=>"bar"}})
    end
  end
end
