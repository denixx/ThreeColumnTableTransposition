package net.denixx.tctt;

import lombok.Value;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Converter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Converter.class);

    public static final String CONVERTER_NAME = "Транспонирование 3-колоночных CSV";

    static private final NumberFormat nf = NumberFormat.getNumberInstance();
    static {
        nf.setMinimumFractionDigits(9);
        nf.setGroupingUsed(false);
    }

    public static final String MIME_CSV = "text/csv";

    public static void transpondTable(File f) throws IOException {
        {
            String mimeType;

            Tika tika = new Tika();

            try {
                mimeType = tika.detect(f);
            } catch (IOException e) {
                LOGGER.error("File at {} is not readable!", f.getPath(), e);
                return;
            }

            if (!MIME_CSV.equals(mimeType)) {
                LOGGER.error("File {}\nhas unsupported mimetype \"{}\"!\n" +
                                "This app understands only \"" + MIME_CSV + "\" (.csv extension).",
                        f.getPath(), mimeType);
                return;
            }
        }

        Map<String, List<CountToDate>> mapOfValuesCounts = new HashMap<>();

        {
            Map<String, DateToListOfValuesToCount> mapOfRecords = new HashMap<>();

            try (Reader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                CSVParser csvParser = CSVFormat
                        .RFC4180
                        .withFirstRecordAsHeader()
                        .withDelimiter(' ')
                        .parse(rdr);

                List<CSVRecord> records = csvParser.getRecords();
                records.forEach(r -> {
                    if (r.size() != 3) {
                        throw new RuntimeException("I can work with only 3 columns! line: [" + r.toString() + "]");
                    }
                    String date = r.get(0);
                    String value = r.get(1);
                    String count = r.get(2);

                    long parsedCount;

                    try {
                        parsedCount = Long.parseLong(count);
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Bad row format! Third column should be Long. line: [" + r.toString() + "]");
                    }

                    ValueToCount valueToCount = new ValueToCount(value, parsedCount);

                    if (mapOfRecords.get(date) != null) {
                        mapOfRecords.get(date).getValueToCountList().add(valueToCount);
                    } else {
                        List<ValueToCount> list = new ArrayList<>();
                        list.add(valueToCount);
                        mapOfRecords.put(date, new DateToListOfValuesToCount(date, list));
                    }
                });
            }

            mapOfRecords.values().forEach(v2cl -> {
                v2cl.getValueToCountList().forEach(v2c -> {
                    String value = v2c.getValue();
                    Long count = v2c.getCount();

                    if (mapOfValuesCounts.get(value) != null) {
                        mapOfValuesCounts.get(value).add(new CountToDate(count, v2cl.getDate()));
                    } else {
                        List<CountToDate> list = new ArrayList<>();
                        list.add(new CountToDate(count, v2cl.getDate()));
                        mapOfValuesCounts.put(value, list);
                    }
                });
            });
        }

        List<ValueToCountToDateList> listOfValueToCountToDateLists;
        ArrayList<String> datesUniqueList;

        {
            LinkedHashMap<String, List<CountToDate>> sortedMapOfValuesCounts = new LinkedHashMap<>();

            mapOfValuesCounts.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.comparingInt(list -> -list.size())))
                    .forEachOrdered(x -> sortedMapOfValuesCounts.put(x.getKey(), x.getValue()));

            listOfValueToCountToDateLists = sortedMapOfValuesCounts.entrySet().stream()
                    .map(e -> new ValueToCountToDateList(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());

            TreeSet<String> datesUniqueSet = new TreeSet<>();

            listOfValueToCountToDateLists.forEach(valueToCountToDateList -> {
                Set<String> dates = valueToCountToDateList.getCountToDateList().stream()
                        .map(CountToDate::getDate)
                        .collect(Collectors.toSet());

                datesUniqueSet.addAll(dates);
            });

            datesUniqueList = new ArrayList<>(datesUniqueSet);
            datesUniqueList.sort(String::compareTo);

            LOGGER.info("Unique dates list: {}", datesUniqueList);
        }

        final int maxSizeOfDatesFinal = datesUniqueList.size();
        LOGGER.info("dates count: {}", maxSizeOfDatesFinal);

        List<ValueToListOfCountToDateWithAverages> val2CountWDateWAvgsList;

        {
            val2CountWDateWAvgsList = listOfValueToCountToDateLists.stream()
                    .map(valueToCountToDateList -> {
                        long sumOfCounts = valueToCountToDateList.countToDateList.stream().mapToLong(CountToDate::getCount).sum();
                        return new ValueToListOfCountToDateWithAverages(
                                valueToCountToDateList.getValue(),
                                valueToCountToDateList.getCountToDateList(),
                                (double) sumOfCounts / maxSizeOfDatesFinal,
                                (double) sumOfCounts / valueToCountToDateList.getCountToDateList().size());
                    })
                    .sorted(Comparator.comparing(ValueToListOfCountToDateWithAverages::getAvgByAll).reversed())
                    .collect(Collectors.toList());

            val2CountWDateWAvgsList.forEach(valueToListOfCountToDateWithAverages -> {
                valueToListOfCountToDateWithAverages.getCountToDateList().sort(Comparator.comparing(CountToDate::getDate));
            });
        }

        LOGGER.info("result size: {}", val2CountWDateWAvgsList.size());

        if (val2CountWDateWAvgsList.size() > 6) {
            LOGGER.info("First 3 result elements: {}", val2CountWDateWAvgsList.subList(0, 3));
            LOGGER.info("Last  3 result elements: {}",
                    val2CountWDateWAvgsList.subList(val2CountWDateWAvgsList.size()-4, val2CountWDateWAvgsList.size()-1));
        } else {
            LOGGER.info("result elements: {}", val2CountWDateWAvgsList);
        }



        File extractedFile = new File(f.getParent(),
                f.getName()
                        .replaceFirst("\\.csv?$", "") + "_extracted.csv");

        LOGGER.info("Fill output results to file: {}", extractedFile.getAbsolutePath());

        try (CSVPrinter csvPrinter =
                     CSVFormat
                             .RFC4180
                             .withFirstRecordAsHeader()
                             .withDelimiter(';')
                             .print(extractedFile, StandardCharsets.UTF_8))
        {
            try {
                csvPrinter.print("value");
                csvPrinter.print("avgByAll");
                csvPrinter.print("avgByNE");
                datesUniqueList.forEach(s -> {
                    try {
                        csvPrinter.print(s);
                    } catch (IOException e) {
                        throw new RuntimeException("Can't output CSV-file!", e);
                    }
                });
                csvPrinter.println();
            } catch (IOException e) {
                LOGGER.error("Can't output CSV-file!", e);
            }

            val2CountWDateWAvgsList.forEach(valueToListOfCountToDateWithAverages -> {
                String value = valueToListOfCountToDateWithAverages.getValue();
                Double avgByAll = valueToListOfCountToDateWithAverages.getAvgByAll();
                Double avgByNotEmpty = valueToListOfCountToDateWithAverages.getAvgByNotEmpty();
                List<CountToDate> countToDateList = valueToListOfCountToDateWithAverages.getCountToDateList();
                List<CountToDate> fullDatesCountsList = datesUniqueList.stream().map(s -> {
                    Optional<CountToDate> countToDate1 = countToDateList.stream()
                            .filter(countToDate -> s.equals(countToDate.getDate()))
                            .findAny();
                    return countToDate1.orElseGet(() -> new CountToDate(null, s));
                }).collect(Collectors.toList());

                try {
                    csvPrinter.print(value);
                    csvPrinter.print(nf.format(avgByAll));
                    csvPrinter.print(nf.format(avgByNotEmpty));
                    fullDatesCountsList.forEach(countToDate -> {
                        try {
                            csvPrinter.print(countToDate.getCount());
                        } catch (IOException e) {
                            throw new RuntimeException("Can't output CSV-file!", e);
                        }
                    });
                    csvPrinter.println();
                } catch (IOException e) {
                    throw new RuntimeException("Can't output CSV-file!", e);
                }
            });
        }
    }

    @Value
    public static class DateToListOfValuesToCount {
        String date;
        List<ValueToCount> valueToCountList;
    }

    @Value
    public static class ValueToCount {
        String value;
        Long count;
    }

    @Value
    public static class ValueToCountToDateList {
        String value;
        List<CountToDate> countToDateList;

        @Override
        public String toString() {
            return "{\n" +
                    "\"value\" = \"" + value + "\",\n" +
                    "\"countToDateList\" = " + countToDateList +
                    "\n}";
        }
    }

    @Value
    public static class ValueToListOfCountToDateWithAverages {
        String value;
        List<CountToDate> countToDateList;
        Double avgByAll;
        Double avgByNotEmpty;

        @Override
        public String toString() {
            return "{\n" +
                    "\t\"value\" = \"" + value + "\"," +
                    "\n\t\"countToDateList\" = " + countToDateList +
                    ",\n\t\"avgByAll\" = " + nf.format(avgByAll) +
                    ", \"avgByNotEmpty\" = " + nf.format(avgByNotEmpty) +
                    "\n}";
        }
    }

    @Value
    public static class CountToDate {
        Long count;
        String date;

        @Override
        public String toString() {
            return "{\"count = \"" + count + ", \"date\" = \"" + date + "\"}";
        }
    }
}
