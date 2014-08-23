require 'bundler'
require 'jbundler'
Bundler.require

require 'wrong/adapters/rspec'

def reload!
  Object.send(:remove_const, :Dalton) if defined?(Dalton)
  load './lib/dalton.rb'

  Dir[File.join(File.dirname(__FILE__), 'support/**/*.rb')].each {|f| load(f)}
end

reload!
