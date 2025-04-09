package uk.ac.ebi.protvar.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.response.Message;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputBuild {
    Assembly assembly; // detected build, based on sample size
    Message message;
}
