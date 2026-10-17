import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import java.nio.file.*;
import java.util.*;

/** Real saved DTO hydration, including source initialization and close. Material
 * resolution is omitted by the existing production unit-test entrypoint. */
public final class DhPointCodecBenchmark {
    public static void main(String[]args)throws Exception {
        var manifest=Path.of(args[0]);var data=com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        var sources=new ArrayList<FullDataSourceV2DTO>();
        for(var item:data.getAsJsonArray("rows")){
            var row=item.getAsJsonObject();var dto=FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding();
            dto.pos=DhSectionPos.encode((byte)(row.get("level").getAsInt()+6),row.get("x").getAsInt(),row.get("z").getAsInt());
            dto.dataFormatVersion=(byte)row.get("version").getAsInt();dto.compressionModeValue=(byte)row.get("compression").getAsInt();
            for(var field:row.getAsJsonObject("fields").entrySet())FullDataSourceV2DTO.class.getField(field.getKey()).set(dto,
                ByteArrayList.wrap(Files.readAllBytes(manifest.getParent().resolve(field.getValue().getAsJsonObject().get("file").getAsString()))));
            sources.add(dto);
        }
        long expected=0;
        try {
            for(int round=0;round<45;round++){
                long begin=System.nanoTime(),hash=1;
                for(var dto:sources)try(var source=dto.createUnitTestDataSource()){
                    for(int coordinate:new int[]{1,31,62}){
                        var column=source.getColumnAtRelPos(coordinate,coordinate);
                        hash=hash*31+column.size();if(column.size()>0)hash=hash*31+column.getLong(0);
                    }
                }
                long elapsed=System.nanoTime()-begin;
                if(round==0)expected=hash;else if(hash!=expected)throw new AssertionError("Hydration checksum changed");
                if(round>=15)System.out.println("SAMPLE "+(round-15)+" "+elapsed+" "+hash);
            }
        }finally {for(var dto:sources)dto.close();}
    }
}
