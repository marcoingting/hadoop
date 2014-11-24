package com.hadoop.designpatterns;

import java.io.IOException;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

/**
 * <b>Implementation of the Hadoop 'Stripes' design pattern</b><br>
 * <br>
 * Like the pairs approach, co-occurring word pairs are generated by two nested
 * loops. <br>
 * However, the major difference is that instead of emitting intermediate
 * key-value pairs for each co-occurring word pair, co-occurrence information is
 * first stored in an associative array, denoted H. <br>
 * <b>The mapper</b> emits key-value pairs with words as keys and corresponding
 * associative arrays as values, where each associative array encodes the
 * co-occurrence counts of the neighbors of a particular word (i.e., its
 * context) <br>
 * 
 * <i>(https://chandramanitiwary.wordpress.com/2012/08/19/map-reduce-design-
 * patterns-pairs-stripes/)</i> <br>
 * 
 * @author pmonteiro
 *
 */
public class Stripes extends Configured implements Tool {

	private static final String NEIGHBOURS = "neighbours";
	private static final int NEIGHBOURS_DEFAULT_VALUE = 1;

	public static class MapClass extends Mapper<LongWritable, Text, Text, MapWritable> {

		private Text term = new Text();
		private Text neighbour = new Text();
		private IntWritable neighbourCount = new IntWritable(0);
		private MapWritable associativeMap = new MapWritable();
		private IntWritable one = new IntWritable(1);

		public void map(LongWritable lineNumber, Text line, Context context) throws IOException, InterruptedException {

			int neighbours = context.getConfiguration().getInt(NEIGHBOURS, NEIGHBOURS_DEFAULT_VALUE);
			String[] words = line.toString().toLowerCase().replaceAll("[^a-zA-Z ]", "").split("\\s+");

			for (int i = 0; i < words.length; i++) {
				associativeMap.clear();

				if (words[i].length() == 0) {
					continue;
				}

				term.set(words[i]);
				for (int j = i - neighbours; j < i + neighbours + 1; j++) {

					if (j >= words.length) {
						break;
					}

					if (j == i || j < 0) {
						continue;
					}
					neighbour.set(words[j]);

					if (associativeMap.containsKey(neighbour)) {
						IntWritable count = (IntWritable) associativeMap.get(neighbour);
						neighbourCount.set(count.get() + 1);
						associativeMap.put(neighbour, neighbourCount);
					} else {
						associativeMap.put(neighbour, one);
					}
				}
				context.write(term, associativeMap);
			}
		}
	}

	public static class Reduce extends Reducer<Text, MapWritable, Text, MapWritable> {
		private MapWritable associativeResult = new MapWritable();

		public void reduce(Text term, Iterable<MapWritable> associativeMaps, Context context) throws IOException,
				InterruptedException {

			associativeResult.clear();
			for (MapWritable associativeMap : associativeMaps) {
				sumKeyValues(term, associativeMap);
			}

			context.write(term, associativeResult);
		}

		private void sumKeyValues(Text term, MapWritable associativeMap) {
			Set<Writable> keys = associativeMap.keySet();
			for (Writable key : keys) {
				IntWritable count = (IntWritable) associativeMap.get(key);

				if (associativeResult.containsKey(key)) {
					IntWritable incCount = (IntWritable) associativeResult.get(key);
					incCount.set(incCount.get() + count.get());
					associativeResult.put(key, incCount);
				} else {
					associativeResult.put(key, count);
				}
			}
		}
	}

	public int run(String[] args) throws Exception {
		Configuration conf = new Configuration();
		Job job = new Job(conf, "CitationsGroups");
		if (args.length == 3) {
			job.getConfiguration().set(NEIGHBOURS, args[2]);
		}
		job.setJarByClass(Stripes.class);

		job.setMapperClass(MapClass.class);
		job.setReducerClass(Reduce.class);

		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(MapWritable.class);

		Path in = new Path(args[0]);
		FileInputFormat.setInputPaths(job, in);

		Path out = new Path(args[1]);
		FileSystem fs = FileSystem.get(conf);
		fs.delete(out, true);
		FileOutputFormat.setOutputPath(job, out);

		System.exit(job.waitForCompletion(true) ? 0 : 1);
		return 0;
	}

	public static void main(String[] args) throws Exception {

		String[] parameters = { "assets/mlk_speech/input", "assets/mlk_speech/output", "2" };
		if (args != null && (args.length == 2 || args.length == 3)) {
			parameters = args;
		}
		int res = ToolRunner.run(new Configuration(), new Stripes(), parameters);
		System.exit(res);
	}
}