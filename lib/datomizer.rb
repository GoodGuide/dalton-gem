defined?(RUBY_ENGINE) && RUBY_ENGINE == "jruby" or raise "JRuby required."

require 'rubygems'

require 'lock_jar'
LockJar.load

require 'zweikopf'

module Datomizer
  autoload :Database, File.join(File.dirname(__FILE__), 'datomizer', 'database')
  autoload :Entity, File.join(File.dirname(__FILE__), 'datomizer', 'entity')
end

# We need to shut down the Datomic driver and Clojure runtime
# before exit, or there will be ~20 second delay shutting down the JVM.
at_exit do
  Java::Datomic::Peer.shutdown(true) # true means "also shut down Clojure runtime"
end