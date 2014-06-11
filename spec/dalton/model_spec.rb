require 'spec_helper'

describe Dalton::Model do
  class Sample
    include Dalton::Model

    uri "datomic:mem://spec"
    namespace :dalton
    partition :spec

    schema <<-EDN
      [[:db/add #db/id[:db.part/spec] :db/ident :dalton.type/sample]

       {:db/id #db/id[:db.part/db]
        :db/ident :dalton.sample/foo
        :db/valueType :db.type/string
        :db/cardinality :db.cardinality/one
        :db/doc "Foo attribute"
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :dalton.sample/bar-custom-key
        :db/valueType :db.type/string
        :db/cardinality :db.cardinality/one
        :db/doc "Bar attribute"
        :db.install/_attribute :db.part/db}]
    EDN

    attribute :foo
    attribute :bar, 'dalton.sample/bar-custom-key'

    validation do
      validate :foo do |foo|
        if foo =~ /invalid/
          invalid! "must not contain the string 'invalid'"
        end
      end
    end
  end

  before do
    Sample.install_base!
    Sample.install_schema!
  end

  after do
    Sample.connection.destroy
  end

  describe 'basic model' do
    let(:model) do
      Sample.create! do |m|
        m.foo = 'foo-value'
        m.bar = 'bar-value'
      end
    end

    describe '.create!' do
      it 'creates a model' do
        assert { model.is_a? Sample }
        assert { model.foo == 'foo-value' }
        assert { model.bar == 'bar-value' }
      end
    end

    describe '#change' do
      let(:next_model) do
        model.change! do |m|
          m.foo = 'new-foo-value'
        end
      end

      it 'returns a new model with the changes' do
        assert { next_model.is_a? Sample }
        assert { next_model.foo == 'new-foo-value' }
        assert { next_model.bar == 'bar-value' }
        assert { model.foo == 'foo-value' }
      end
    end

    describe 'validations' do
      let(:validation_error) do
        rescuing {
          model.change! do |m|
            m.foo = 'invalid-foo-value'
          end
        }
      end

      it 'raises a validation error' do
        assert { validation_error.is_a? Dalton::Model::ValidationError }
        assert { validation_error.errors.length == 1 }
        assert { validation_error.errors_on(:foo) == ["must not contain the string 'invalid'"] }
        assert { validation_error.changes.change_in(:foo) == ['foo-value', 'invalid-foo-value'] }
      end
    end

    describe 'finders' do
      let(:finder) do
        model.finder
      end

      describe '#entity' do
        it 'returns the same model' do
          refreshed = finder.entity(model.id)
          assert { refreshed == model }
        end
      end

      describe '#by_{attribute}' do
        it 'returns the same model' do
          by_foo = finder.by_foo('foo-value')
          assert { by_foo.first == model }
        end
      end

      describe '#where' do
        it 'returns the same model' do
          by_bar = finder.where(:bar => 'bar-value')
          assert { by_bar.first == model }
        end
      end
    end
  end
end


