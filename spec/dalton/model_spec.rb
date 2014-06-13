require 'spec_helper'

describe Dalton::Model do
  Dalton::Model.configure(
    :default_namespace => :dalton,
    :default_partition => :spec
  )

  class Sample
    include Dalton::Model

    uri "datomic:mem://spec"

    schema do
      attribute "Foo attribute", :foo, :value_type => :string
      attribute "Bar attribute", :'bar-custom-key', :value_type => :string
      attribute "Parent model", :parent, :value_type => :ref
    end

    attribute :foo, :default => 'foo-default'
    attribute :bar, 'dalton.sample/bar-custom-key'
    attribute :parent
    referenced :children, :type => Sample, :from => :parent

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
        m.bar = 'bar-value'
      end
    end

    describe '.create!' do
      it 'creates a model' do
        assert { model.is_a? Sample }
        assert { model.foo == 'foo-default' }
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
        assert { model.foo == 'foo-default' }
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
        assert { validation_error.changes.change_in(:foo) == ['foo-default', 'invalid-foo-value'] }
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
          by_bar = finder.by_bar('bar-value')
          assert { by_bar.first == model }
        end
      end

      describe '#where' do
        it 'returns the same model' do
          by_bar = finder.where(:bar => 'bar-value')
          assert { by_bar.first == model }
        end
      end
    end

    describe 'relations' do
      it 'starts out nil/empty' do
        assert { model.parent == nil }
        assert { model.children == [] }
      end

      it 'sets a one-to-many relation' do
        next_model = model.change! do |m|
          m.parent = model
        end

        assert { next_model.parent == next_model }
        assert { next_model.children.to_a == [next_model] }
      end

      it 'sets a many-to-one relation' do
        next_model = model.change! do |m|
          m.children = [model]
        end

        assert { next_model.parent == next_model }
        assert { next_model.children.to_a == [next_model] }
      end
    end
  end
end


