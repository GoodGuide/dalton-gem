defined?(RUBY_ENGINE) && RUBY_ENGINE == "jruby" or raise "JRuby required."

require 'rubygems'
require 'jbundler'
require 'zweikopf'
require 'pathname'

module Dalton
  class DatomicError < StandardError
  end
end

load_dir = Pathname.new(__FILE__).dirname
load load_dir.join('dalton/utility.rb')

load load_dir.join('dalton/datomization.rb')
load load_dir.join('dalton/undatomization.rb')

load load_dir.join('dalton/exception.rb')
load load_dir.join('dalton/database.rb')
load load_dir.join('dalton/connection.rb')
load load_dir.join('dalton/entity.rb')
load load_dir.join('dalton/transaction_result.rb')
load load_dir.join('dalton/attribute.rb')
load load_dir.join('dalton/translation.rb')

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
