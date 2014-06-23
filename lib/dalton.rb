defined?(RUBY_ENGINE) && RUBY_ENGINE == "jruby" or raise "JRuby required."

require 'rubygems'
require 'jbundler'
require 'zweikopf'

module Dalton
  class DatomicError < StandardError
  end
end

require_relative('dalton/database')
require_relative('dalton/entity')
require_relative('dalton/transaction_result')
require_relative('dalton/attribute')
require_relative('dalton/utility')
require_relative('dalton/translation')
require_relative('dalton/datomization')

class Object
  def to_edn
    Dalton::Utility.to_edn(self)
  end
end

# We need to shut down the Datomic driver and Clojure runtime
# before exit, or there will be ~20 second delay shutting down the JVM.
at_exit do
  Java::Datomic::Peer.shutdown(true) # true means "also shut down Clojure runtime"
end
