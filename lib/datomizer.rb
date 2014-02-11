defined?(RUBY_ENGINE) && RUBY_ENGINE == "jruby" or raise "JRuby required."

require 'rubygems'

require 'lock_jar'
LockJar.load

require 'zweikopf'

require_relative('datomizer/database')
require_relative('datomizer/entity')
require_relative('datomizer/transaction_result')
require_relative('datomizer/utility')
require_relative('datomizer/translation')
require_relative('datomizer/datomization')

module Datomizer

end

class Object
  def to_edn
    Datomizer::Utility.to_edn(self)
  end
end

# We need to shut down the Datomic driver and Clojure runtime
# before exit, or there will be ~20 second delay shutting down the JVM.
at_exit do
  Java::Datomic::Peer.shutdown(true) # true means "also shut down Clojure runtime"
end
