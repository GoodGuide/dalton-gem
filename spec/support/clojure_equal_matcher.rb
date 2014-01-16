require 'rspec/matchers'

module RSpec
  module Matchers
    module Datomizer
      class ClojureEqual < BuiltIn::BaseMatcher
        def match(expected, actual)
          ::Datomizer::Utility.clojure_equal?(actual, expected)
        end

        def failure_message_for_should
          return <<-MESSAGE

expected #{inspect_object(expected)}
     got #{inspect_object(actual)}

Compared using clojure_equal?, which compares using clojure.core/=

MESSAGE
        end

        def failure_message_for_should_not
          return <<-MESSAGE

expected not #{inspect_object(actual)}
         got #{inspect_object(expected)}

Compared using clojure_equal?, which compares using clojure.core/=

MESSAGE
        end

        def diffable?; true; end

        private

        def inspect_object(o)
          "#<#{o.class}:#{o.object_id}> => #{::Datomizer::Utility.to_edn(o)}"
        end

        def eq_expression
          Expectations::Syntax.positive_expression("actual", "clojure_equal?(expected)")
        end
      end
    end

    def clojure_equal(expected)
      Datomizer::ClojureEqual.new(expected)
    end

  end
end
