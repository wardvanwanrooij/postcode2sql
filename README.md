# postcode2sql

## Overview
postcode2sql converts the Kadaster BAG open dataset to SQL for querying postcodes (zipcodes in The Netherlands). It doesn't require the user to import the complete Kadaster BAG dataset, instead it will parse the inspireadressen.zip (provided by the Kadaster) in memory and create a SQL file containing all postcode ranges. This can then be imported in a database and queried.
Latest version at https://github.com/wardvanwanrooij/postcode2sql

## Running postcode2sql

Run the supplied JAR file and provide the location of inspireadressen.zip and the location of the SQL output. You can download the latest inspireadressen from http://geodata.nationaalgeoregister.nl/inspireadressen/extract/inspireadressen.zip . The BAG data is supplemented with algorithmically generated postbus (PO box) data.

	$ wget http://geodata.nationaalgeoregister.nl/inspireadressen/extract/inspireadressen.zip
	[...]
	$ java -jar postcode2sql.jar inspireadressen.zip postcode.sql
	Jul 02, 2018 8:20:25 PM nu.ward.postcode2sql.Converter main
	INFO: started postcode2sql build 20180702
	Jul 02, 2018 8:20:25 PM nu.ward.postcode2sql.Converter read
	INFO: reading from inspireadressen.zip
	Jul 02, 2018 8:22:20 PM nu.ward.postcode2sql.Converter fixup
	INFO: fixing up intersecting data
	Jul 02, 2018 8:22:20 PM nu.ward.postcode2sql.Converter determineRange
	INFO: determining continuous ranges
	Jul 02, 2018 8:22:21 PM nu.ward.postcode2sql.Converter fillPOBox
	INFO: inserting post office boxes
	Jul 02, 2018 8:22:21 PM nu.ward.postcode2sql.Converter write
	INFO: writing to postcode.sql
	Jul 02, 2018 8:22:24 PM nu.ward.postcode2sql.Converter main
	INFO: finished
	$       

## Loading postcode SQL data

Create a table reeks with this structure:

	CREATE TABLE reeks (id BIGSERIAL PRIMARY KEY, postcode_numeriek SMALLINT NOT NULL, postcode_letters CHAR(2), straat VARCHAR NOT NULL, plaats VARCHAR NOT NULL, huisnummer_start INT4, huisnummer_einde INT4, huisnummer_even BOOLEAN NOT NULL, huisnummer_oneven BOOLEAN NOT NULL);
	CREATE INDEX reeks_postcode_numeriek ON reeks(postcode_numeriek);

More information in sql.txt

## Querying postcode SQL data

Use this query to selected the street and city, given the postcode 1313GV and housenumber 32.

	postcode=> SELECT * FROM reeks WHERE postcode_numeriek = 1313 AND (postcode_letters IS NULL OR postcode_letters = 'GV') AND (huisnummer_start IS NULL OR huisnummer_start <= 32) AND (huisnummer_einde IS NULL OR huisnummer_einde >= 32) AND (huisnummer_even OR (32 % 2 = 1)) AND (huisnummer_oneven OR (32 % 2 = 0));
	  id   | postcode_numeriek | postcode_letters |   straat    | plaats | huisnummer_start | huisnummer_einde | huisnummer_even | huisnummer_oneven 
	-------+-------------------+------------------+-------------+--------+------------------+------------------+-----------------+-------------------
	 30250 |              1313 | GV               | Sesamstraat | Almere |               28 |               58 | t               | f
	(1 row)
	postcode=> 

You must inlude the NULL checks to be able to match a postbus (PO box). Since the postbus data is generated, it may be incorrect. In particular, a given combination may validate as a postbus, but may in reality be non-existent.

## License

postcode2sql is licensed under the MIT license, for more information see the file LICENSE.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
