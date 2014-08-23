require 'bundler'
require 'jbundler'
Bundler.require

require 'wrong/adapters/rspec'

def reload!
  load './lib/dalton.rb'

  Dir[File.join(File.dirname(__FILE__), 'support/**/*.rb')].each {|f| load(f)}
end

reload!
