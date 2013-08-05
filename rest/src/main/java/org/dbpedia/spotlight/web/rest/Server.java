/*
 * Copyright 2011 DBpedia Spotlight Development Team
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Check our project website for information on how to acknowledge the authors and how to contribute to the project: http://spotlight.dbpedia.org
 */

package org.dbpedia.spotlight.web.rest;

import com.sun.grizzly.http.SelectorThread;
import com.sun.jersey.api.container.grizzly.GrizzlyWebContainerFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbpedia.spotlight.db.SpotlightModel;
import org.dbpedia.spotlight.db.model.TextTokenizer;
import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguatorJ;
import org.dbpedia.spotlight.exceptions.InitializationException;
import org.dbpedia.spotlight.exceptions.InputException;
import org.dbpedia.spotlight.filter.annotations.CombineAllAnnotationFilters;
import org.dbpedia.spotlight.model.DBpediaResource;
import org.dbpedia.spotlight.model.SpotlightConfiguration;
import org.dbpedia.spotlight.model.SpotlightFactory;
import org.dbpedia.spotlight.model.SpotterConfiguration;
import org.dbpedia.spotlight.spot.Spotter;
import org.dbpedia.spotlight.model.SpotterConfiguration.SpotterPolicy;
import org.dbpedia.spotlight.model.SpotlightConfiguration.DisambiguationPolicy;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.net.*;

/**
 * Instantiates Web Service that will execute annotation and disambiguation tasks.
 *
 * @author maxjakob
 * @author pablomendes - added WADL generator config, changed to Grizzly
 */

public class Server {
    static Log LOG = LogFactory.getLog  (Server.class);

    public static final String APPLICATION_PATH = "http://spotlight.dbpedia.org/rest";

    // Server reads configuration parameters into this static configuration object that will be used by other classes downstream
    protected static SpotlightConfiguration configuration;

    // Server will hold a few spotters that can be chosen from URL parameters
    protected static Map<SpotterPolicy,Spotter> spotters = new HashMap<SpotterConfiguration.SpotterPolicy,Spotter>();

    // Server will hold a few disambiguators that can be chosen from URL parameters
    protected static Map<DisambiguationPolicy,ParagraphDisambiguatorJ> disambiguators = new HashMap<SpotlightConfiguration.DisambiguationPolicy,ParagraphDisambiguatorJ>();

    private static volatile Boolean running = true;

    static String usage = "usage: java -jar dbpedia-spotlight.jar org.dbpedia.spotlight.web.rest.Server [config file]"
                        + "   or: mvn scala:run \"-DaddArgs=[config file]\"";

    //This is currently only used in the DB-based version.
    private static TextTokenizer tokenizer;

    protected static CombineAllAnnotationFilters combinedFilters = null;

    private static String namespacePrefix = SpotlightConfiguration.DEFAULT_NAMESPACE;

    public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException, ClassNotFoundException, InitializationException {

        URI serverURI = null;

        if(args[0].endsWith(".properties")) {

            //We are using the old-style configuration file:

            //Initialization, check values
            try {
                String configFileName = args[0];
                configuration = new SpotlightConfiguration(configFileName);
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("\n"+usage);
                System.exit(1);
            }

            serverURI = new URI(configuration.getServerURI());

            // Set static annotator that will be used by Annotate and Disambiguate
            final SpotlightFactory factory = new SpotlightFactory(configuration);

            setDisambiguators(factory.disambiguators());
            setSpotters(factory.spotters());
            setNamespacePrefix(configuration.getDbpediaResource());

            setCombinedFilters(new CombineAllAnnotationFilters(Server.getConfiguration()));


        } else {
            //We are using a model folder:

            serverURI = new URI(args[1]);

            File modelFolder = null;
            try {
                modelFolder = new File(args[0]);
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("\n"+usage);
                System.exit(1);
            }

            SpotlightModel db = SpotlightModel.fromFolder(modelFolder);


            setNamespacePrefix(db.properties().getProperty("namespace"));
            setTokenizer(db.tokenizer());
            setSpotters(db.spotters());
            setDisambiguators(db.disambiguators());

        }

        //ExternalUriWadlGeneratorConfig.setUri(configuration.getServerURI()); //TODO get another parameter, maybe getExternalServerURI since Grizzly will use this in order to find out to which port to bind


        LOG.info(String.format("Initiated %d disambiguators.",disambiguators.size()));
        LOG.info(String.format("Initiated %d spotters.",spotters.size()));
        //System.exit(0);

        final Map<String, String> initParams = new HashMap<String, String>();
        initParams.put("com.sun.jersey.config.property.resourceConfigClass", "com.sun.jersey.api.core.PackagesResourceConfig");
        initParams.put("com.sun.jersey.config.property.packages", "org.dbpedia.spotlight.web.rest.resources");
        initParams.put("com.sun.jersey.config.property.WadlGeneratorConfig", "org.dbpedia.spotlight.web.rest.wadl.ExternalUriWadlGeneratorConfig");


        SelectorThread threadSelector = GrizzlyWebContainerFactory.create(serverURI, initParams);
        threadSelector.start();

        System.err.println("Server started in " + System.getProperty("user.dir") + " listening on " + serverURI);

        //Open browser
        try {
            String example1 = "annotate?text=At%20a%20private%20dinner%20on%20Friday%20at%20the%20Canadian%20Embassy,%20finance%20officials%20from%20seven%20world%20economic%20powers%20focused%20on%20the%20most%20vexing%20international%20economic%20problem%20facing%20the%20Obama%20administration.%20Over%20seared%20scallops%20and%20beef%20tenderloin,%20Treasury%20Secretary%20Timothy%20F.%20Geithner%20urged%20his%20counterparts%20from%20Europe,%20Canada%20and%20Japan%20to%20help%20persuade%20China%20to%20let%20its%20currency,%20the%20renminbi,%20rise%20in%20value%20a%20crucial%20element%20in%20redressing%20the%20trade%20imbalances%20that%20are%20threatening%20recovery%20around%20the%20world.%20But%20the%20next%20afternoon,%20the%20annual%20meetings%20of%20the%20International%20Monetary%20Fund%20ended%20with%20a%20tepid%20statement%20that%20made%20only%20fleeting%20and%20indirect%20references%20to%20the%20simmering%20currency%20tensions&confidence=0.2&support=20";
            String example2 = "annotate?text=Brazilian%20oil%20giant%20Petrobras%20and%20U.S.%20oilfield%20service%20company%20Halliburton%20have%20signed%20a%20technological%20cooperation%20agreement,%20Petrobras%20announced%20Monday.%20%20%20%20The%20two%20companies%20agreed%20on%20three%20projects:%20studies%20on%20contamination%20of%20fluids%20in%20oil%20wells,%20laboratory%20simulation%20of%20well%20production,%20and%20research%20on%20solidification%20of%20salt%20and%20carbon%20dioxide%20formations,%20said%20Petrobras.%20Twelve%20other%20projects%20are%20still%20under%20negotiation.&confidence=0.0&support=0";
            String example3 = "Pego%20no%20doping,%20Michael%20admite%20a%20dirigentes%20que%20fez%20uso%20de%20cocaína%20Sandro%20Lima%20e%20Rodrigo%20Caetano%20dizem%20que%20clube%20vai%20oferecer%20apoio%20ao%20atacante,%20que%20aparece%20para%20treinar%20normalmente%20nas%20Laranjeiras%20Por%20Edgard%20Maciel%20de%20Sá%20e%20Fabio%20Leme%20Rio%20de%20Janeiro%20Rodrigo%20Caetano%20e%20Sandro%20Lima%20falam%20sobre%20o%20doping%20de%20Michael%20(Foto:%20Fabio%20Leme)%20O%20vice-presidente%20de%20futebol%20do%20Fluminense,%20Sandro%20Lima,%20revelou%20na%20tarde%20desta%20terça-feira%20que%20a%20substância%20encontrada%20no%20organismo%20do%20atacante%20Michael%20foi%20cocaína.%20O%20jogador%20foi%20flagrado%20no%20exame%20antidoping%20na%20vitória%20sobre%20o%20Resende%20por%202%20a%200,%20no%20último%20dia%206%20de%20abril,%20em%20Volta%20Redonda,%20e%20admitiu%20aos%20dirigentes%20o%20uso%20da%20droga.%20Sandro%20Lima%20participou%20de%20entrevista%20coletiva%20nas%20Laranjeiras%20ao%20lado%20do%20diretor%20executivo%20de%20futebol,%20Rodrigo%20Caetano,%20para%20comentar%20o%20segundo%20caso%20de%20doping%20do%20Tricolor%20em%20uma%20semana.%20-%20Estamos%20aqui%20para%20confirmar%20o%20que%20vocês%20já%20sabem%20(caso%20de%20doping).%20A%20substância%20foi%20cocaína,%20fomos%20pegos%20de%20surpresa.%20Ficamos%20entristecidos.%20Já%20encaminhamos%20o%20atleta%20para%20o%20departamento%20médico%20para%20prosseguir%20o%20tratamento,%20e%20o%20que%20for%20possível%20o%20Fluminense%20vai%20fazer.%20Nós%20chamamos%20o%20Michael,%20ele%20não%20negou%20e%20pediu%20que%20nós%20o%20ajudássemos%20-%20afirmou%20Sandrão.%20Nesse%20caso,%20nós%20chamamos%20o%20atleta,%20que%20acabou%20confirmando%20o%20uso,%20e%20portanto,%20em%20conjunto%20com%20todos%20os%20departamentos%20do%20clube,%20optou-se%20por%20não%20fazer%20a%20contraprova,%20e%20sim%20um%20encaminhamento%20ao%20departamento%20médico%20e%20jurídico&quot;%20Rodrigo%20Caetano%20Rodrigo%20Caetano%20confirmou%20que%20Michael%20admitiu%20o%20uso%20de%20cocaína%20em%20conversa%20com%20os%20dirigentes%20do%20Fluminense.%20Ele%20prometeu%20apoiar%20o%20jogador%20a%20superar%20a%20dificuldade%20e%20voltar%20aos%20campos%20depois%20de%20cumprir%20a%20suspensão.%20-%20Ninguém%20gosta%20de%20passar%20por%20essas%20situações,%20mas%20é%20uma%20realidade%20do%20nosso%20país,%20uma%20questão%20sociocultural%20que%20temos%20de%20enfrentar.%20Nesse%20caso,%20nós%20chamamos%20o%20atleta,%20que%20acabou%20confirmando%20o%20uso,%20e%20portanto,%20em%20conjunto%20com%20todos%20os%20departamentos%20do%20clube,%20optou-se%20por%20não%20fazer%20a%20contraprova,%20e%20sim%20um%20encaminhamento%20ao%20departamento%20médico%20e%20jurídico.%20Não%20sabemos%20que%20prazo%20isso%20vem%20acontecendo.%20Temos%20que%20ter%20cautela%20para%20preservar%20o%20jogador.%20Temos%20que%20fazer%20a%20nossa%20parte.%20Tratá-lo,%20cuidá-lo%20e%20passar%20para%20quem%20é%20da%20área.%20É%20isso%20que%20vamos%20fazer.%20Foi%20o%20segundo%20antidoping%20positivo%20no%20Tricolor%20em%20uma%20semana.%20No%20último%20dia%2030,%20Deco%20foi%20flagrado%20em%20exame%20por%20uso%20de%20furosemida,%20diurético%20presente%20em%20vitaminas%20vendidas%20em%20farmácia%20de%20manipulação.%20Rodrigo%20Caetano%20acredita%20que%20são%20casos%20diferentes.%20-%20Foram%20dois%20casos%20de%20doping,%20mas%20dois%20casos%20distintos.%20É%20difícil%20não%20associar,%20mas%20precisamos%20tratar%20cada%20caso%20a%20sua%20maneira.%20Nosso%20trabalho%20segue%20sempre%20no%20intuito%20de%20educá-los%20quanto%20ao%20risco%20que%20isso%20traz.%20Conversei%20com%20o%20Deco%20no%20dia%20da%20notificação.%20Ele%20já%20toma%20esse%20suplemento%20desde%20que%20retornou%20ao%20Brasil.%20Ele%20fez%20seis%20exames%20desde%20então%20e%20nenhum%20deles%20acusou%20nada.%20Por%20isso%20acreditamos%20que%20algo%20aconteceu%20-%20disse%20o%20diretor.%20Com%20a%20admissão%20do%20uso%20de%20cocaína%20por%20parte%20de%20Michael,%20o%20clube%20não%20vai%20pedir%20a%20realização%20da%20contraprova.%20Assim,%20o%20atacante%20será%20suspenso%20preventivamente%20por%2030%20dias.%20Os%20dirigentes%20já%20avisaram%20que,%20se%20o%20Fluminense%20passar%20às%20quartas%20de%20final%20da%20Libertadores,%20outro%20jogador%20será%20inscrito%20na%20vaga%20de%20Michael.%20O%20Tricolor%20enfrenta%20o%20Emelec%20nesta%20quarta,%20às%2022h,%20em%20São%20Januário,%20depois%20de%20perder%20por%202%20a%201%20em%20Guayaquil,%20na%20última%20quinta,%20no%20jogo%20de%20ida%20das%20oitavas%20de%20final.%20Clube%20promete%20atenção%20a%20drogas%20ilícitas%20em%20Xerém%20Sandro%20Lima%20afirmou%20que%20o%20caso%20de%20Michael%20vai%20se%20tornar%20um%20marco%20no%20clube%20no%20tratamento%20à%20questão%20do%20uso%20de%20drogas%20ilícitas.%20De%20acordo%20com%20o%20vice%20de%20futebol,%20o%20Tricolor%20agora%20vai%20prestar%20atenção%20redobrada%20aos%20garotos%20revelados%20nas%20categorias%20de%20base.%20-%20Vamos%20usar%20esse%20caso%20como%20ponto%20de%20partida%20para%20educar,%20principalmente%20a%20base%20em%20Xerém.%20Eu%20e%20Rodrigo%20somos%20chatos%20na%20cobrança.%20Monitoramos%20o%20menino%20de%20Xerém,%20o%20Fred%20e%20o%20Deco.%20Ainda%20mais%20agora%20em%20uma%20reta%20decisiva.%20Michael%20passa%20a%20ser%20ponto%20de%20partida%20para%20a%20gente%20se%20preocupar%20com%20essas%20drogas%20ilícitas.%20Jogador%20aparece%20para%20treinar%20Michael%20faz%20alongamento%20antes%20do%20treino%20desta%20terça,%20nas%20Laranjeiras%20(Foto:%20Fabio%20Leme)%20Horas%20depois%20da%20divulgação%20do%20seu%20caso%20de%20doping,%20Michael%20apareceu%20para%20treinar%20normalmente%20nas%20Laranjeiras%20na%20tarde%20desta%20terça-feira.%20Rodrigo%20Caetano%20disse%20que%20a%20família%20do%20jogador%20foi%20comunicada%20sobre%20o%20doping%20antes%20da%20divulgação.%20-%20Michael%20é%20de%20Minas%20Gerais.%20Claro%20que%20hoje%20ele%20ainda%20está%20sem%20a%20família%20no%20Rio.%20Eles%20já%20foram%20comunicados.%20Está%20tudo%20muito%20recente.%20Queremos%20ouvir%20especialistas%20para%20saber%20em%20que%20o%20clube%20pode%20ajudar%20nessa%20possível%20recuperação%20do%20atleta.%20O%20atacante,%20que%20chegou%20às%20categorias%20de%20base%20do%20Flu%20em%202011%20-%20depois%20de%20se%20destacar%20na%20Copa%20São%20Paulo%20de%20juniores%20pelo%20Rio%20Preto-SP%20-,%20ganhou%20espaço%20no%20elenco%20principal%20nesta%20temporada%20e%20se%20tornou%20opção%20para%20o%20comando%20de%20ataque%20tricolor%20nos%20jogos%20em%20que%20o%20titular%20Fred%20não%20está%20à%20disposição%20do%20técnico%20Abel%20Braga.%20Ele%20marcou%20quatro%20gols%20no%20Carioca,%20três%20deles%20na%20vitória%20sobre%20o%20Macaé%20por%203%20a%201,%20no%20dia%2027%20de%20março.%20Na%20infância,%20Michael%20chegou%20a%20trabalhar%20como%20ajudante%20de%20pedreiro%20.%20Recentemente,%20o%20jogador%20ainda%20foi%20convocado%20para%20a%20seleção%20brasileira%20sub-20%20para%20participar%20de%20uma%20série%20de%20jogos%20no%20Espirito%20Santo%20e%20na%20Europa.%20Em%202007,%20clube%20dispensou%20Renato%20Silva%20por%20uso%20de%20maconha%20Na%20última%20vez%20em%20que%20um%20jogador%20do%20Fluminense%20foi%20flagrado%20no%20antidoping%20com%20o%20uso%20de%20uma%20droga%20social,%20a%20atitude%20da%20diretoria%20foi%20diferente%20da%20atual,%20que%20promete%20apoio%20a%20Michael.%20Em%202007,%20um%20exame%20feito%20num%20jogo%20contra%20o%20Volta%20Redonda,%20pela%20Taça%20Guanabara,%20apontou%20maconha%20no%20organismo%20do%20zagueiro%20Renato%20Silva.%20Depois%20da%20divulgação%20do%20caso,%20o%20jogador%20foi%20demitido%20por%20justa%20causa.&confidence=0.0&support=0";
            String example4 = "Pego no doping, Michael admite a dirigentes que fez uso de cocaína Sandro Lima e Rodrigo Caetano dizem que clube vai oferecer apoio ao atacante, que aparece para treinar normalmente nas Laranjeiras Por Edgard Maciel de Sá e Fabio Leme Rio de Janeiro Rodrigo Caetano e Sandro Lima falam sobre o doping de Michael (Foto: Fabio Leme) O vice-presidente de futebol do Fluminense, Sandro Lima, revelou na tarde desta terça-feira que a substância encontrada no organismo do atacante Michael foi cocaína. O jogador foi flagrado no exame antidoping na vitória sobre o Resende por 2 a 0, no último dia 6 de abril, em Volta Redonda, e admitiu aos dirigentes o uso da droga. Sandro Lima participou de entrevista coletiva nas Laranjeiras ao lado do diretor executivo de futebol, Rodrigo Caetano, para comentar o segundo caso de doping do Tricolor em uma semana. - Estamos aqui para confirmar o que vocês já sabem (caso de doping). A substância foi cocaína, fomos pegos de surpresa. Ficamos entristecidos. Já encaminhamos o atleta para o departamento médico para prosseguir o tratamento, e o que for possível o Fluminense vai fazer. Nós chamamos o Michael, ele não negou e pediu que nós o ajudássemos - afirmou Sandrão. Nesse caso, nós chamamos o atleta, que acabou confirmando o uso, e portanto, em conjunto com todos os departamentos do clube, optou-se por não fazer a contraprova, e sim um encaminhamento ao departamento médico e jurídico&quot; Rodrigo Caetano Rodrigo Caetano confirmou que Michael admitiu o uso de cocaína em conversa com os dirigentes do Fluminense. Ele prometeu apoiar o jogador a superar a dificuldade e voltar aos campos depois de cumprir a suspensão. - Ninguém gosta de passar por essas situações, mas é uma realidade do nosso país, uma questão sociocultural que temos de enfrentar. Nesse caso, nós chamamos o atleta, que acabou confirmando o uso, e portanto, em conjunto com todos os departamentos do clube, optou-se por não fazer a contraprova, e sim um encaminhamento ao departamento médico e jurídico. Não sabemos que prazo isso vem acontecendo. Temos que ter cautela para preservar o jogador. Temos que fazer a nossa parte. Tratá-lo, cuidá-lo e passar para quem é da área. É isso que vamos fazer. Foi o segundo antidoping positivo no Tricolor em uma semana. No último dia 30, Deco foi flagrado em exame por uso de furosemida, diurético presente em vitaminas vendidas em farmácia de manipulação. Rodrigo Caetano acredita que são casos diferentes. - Foram dois casos de doping, mas dois casos distintos. É difícil não associar, mas precisamos tratar cada caso a sua maneira. Nosso trabalho segue sempre no intuito de educá-los quanto ao risco que isso traz. Conversei com o Deco no dia da notificação. Ele já toma esse suplemento desde que retornou ao Brasil. Ele fez seis exames desde então e nenhum deles acusou nada. Por isso acreditamos que algo aconteceu - disse o diretor. Com a admissão do uso de cocaína por parte de Michael, o clube não vai pedir a realização da contraprova. Assim, o atacante será suspenso preventivamente por 30 dias. Os dirigentes já avisaram que, se o Fluminense passar às quartas de final da Libertadores, outro jogador será inscrito na vaga de Michael. O Tricolor enfrenta o Emelec nesta quarta, às 22h, em São Januário, depois de perder por 2 a 1 em Guayaquil, na última quinta, no jogo de ida das oitavas de final. Clube promete atenção a drogas ilícitas em Xerém Sandro Lima afirmou que o caso de Michael vai se tornar um marco no clube no tratamento à questão do uso de drogas ilícitas. De acordo com o vice de futebol, o Tricolor agora vai prestar atenção redobrada aos garotos revelados nas categorias de base. - Vamos usar esse caso como ponto de partida para educar, principalmente a base em Xerém. Eu e Rodrigo somos chatos na cobrança. Monitoramos o menino de Xerém, o Fred e o Deco. Ainda mais agora em uma reta decisiva. Michael passa a ser ponto de partida para a gente se preocupar com essas drogas ilícitas. Jogador aparece para treinar Michael faz alongamento antes do treino desta terça, nas Laranjeiras (Foto: Fabio Leme) Horas depois da divulgação do seu caso de doping, Michael apareceu para treinar normalmente nas Laranjeiras na tarde desta terça-feira. Rodrigo Caetano disse que a família do jogador foi comunicada sobre o doping antes da divulgação. - Michael é de Minas Gerais. Claro que hoje ele ainda está sem a família no Rio. Eles já foram comunicados. Está tudo muito recente. Queremos ouvir especialistas para saber em que o clube pode ajudar nessa possível recuperação do atleta. O atacante, que chegou às categorias de base do Flu em 2011 - depois de se destacar na Copa São Paulo de juniores pelo Rio Preto-SP -, ganhou espaço no elenco principal nesta temporada e se tornou opção para o comando de ataque tricolor nos jogos em que o titular Fred não está à disposição do técnico Abel Braga. Ele marcou quatro gols no Carioca, três deles na vitória sobre o Macaé por 3 a 1, no dia 27 de março. Na infância, Michael chegou a trabalhar como ajudante de pedreiro . Recentemente, o jogador ainda foi convocado para a seleção brasileira sub-20 para participar de uma série de jogos no Espirito Santo e na Europa. Em 2007, clube dispensou Renato Silva por uso de maconha Na última vez em que um jogador do Fluminense foi flagrado no antidoping com o uso de uma droga social, a atitude da diretoria foi diferente da atual, que promete apoio a Michael. Em 2007, um exame feito num jogo contra o Volta Redonda, pela Taça Guanabara, apontou maconha no organismo do zagueiro Renato Silva. Depois da divulgação do caso, o jogador foi demitido por justa causa.&confidence=0.0&support=0";
            String example5 = "Dilma vai à Argentina nesta quinta para encontro com Cristina Kirchner. A pauta inclui cooperação em ciência, energia, defesa e educação. Situação de Mercosul com novo presidente do Paraguai deve ser tratada. Priscilla Mendes Do G1, em Brasília Cristina Kirchner e Dilma Rousseff, se encontram em Roma após a missa do Papa Francisco (Foto: Reuters/Presidência da Argentina) A presidente Dilma Rousseff marcou para quinta-feira (25) uma viagem à Argentina, segundo informou o Palácio do Planalto. Ela afirmou nesta terça-feira (23) que se encontrará com a presidente daquele país, Cristina Kirchner, para discutir relações comerciais e expansão de investimentos. Dilma deverá passar dois dias na Argentina, de acordo com informação do Itamaraty. A presidente disse que, na reunião – que geralmente ocorre de três em três meses, segundo ela – discutirá com Kirchner “todos os assuntos”. “Nós teremos uma pauta bastante ampla com a Argentina. Sempre discutimos todas as relações, comerciais, investimentos, toda interação entre a economia brasileira e a economia argentina”, afirmou a presidente durante entrevista no Palácio do Planalto. O Itamaraty informou que as duas líderes deverão tratar de projetos de cooperação em ciência, tecnologia, inovação e sustentabilidade em temas como energia nuclear, defesa, empreendimentos hidrelétricos, construção de satélites, indústria naval e educação. saiba mais Dilma telefona para Maduro e diz estar 'pronta' para trabalhar com ele Dilma e Kirchner também deverão tratar da situação dos dois principais blocos da América do Sul, o Mercosul e a Unasul. As cúpulas avaliam a reintegração do Paraguai, suspenso em junho do ano passado após a destituição do então presidente Fernando Lugo.  O novo presidente, Horacio Cartes, foi eleito no último domingo (21). Em telefonema a Cartes, Dilma desejou “um governo bem-sucedido e ressaltou a disposição para recompor as relações bilaterais e do Paraguai com o Mercosul”, informou assessoria do Planalto nesta segunda-feira (22). A Argentina é o terceiro maior parceiro comercial do Brasil e o principal destino das exportações nacionais de manufaturas, segundo informou o Itamaraty. De 2003 a 2012, o comércio entre Brasil e Argentina passou de US$ 9,24 bilhões para mais de US$ 34,4 bilhões.&confidence=0.0&support=0";
            String example6 = "Construtora vende do nome da Arena Palestra, que será 'xará' do Bayern WTorre fecha primeiro grande contrato comercial do estádio. Evento na segunda-feira vai apresentar detalhes da parceria com empresa alemã Por GLOBOESPORTE.COM São Paulo Arena Palestra deve ser inaugurada somente no ano que vem (Foto: Flavio Canuto) A WTorre acertou o primeiro grande contrato comercial da Arena Palestra. Nesta quarta-feira, a construtora responsável pela reforma da casa palmeirense anunciou a venda de naming rights do estádio para a Allianz Seguros. Na próxima segunda-feira, dia 29 de abril, um evento apresentará ao público detalhes do acordo. A empresa alemã já possui os direitos dos nomes de quatro arenas em todo o mundo, entre eles a do Bayern de Munique, que abrigou jogos da Copa do Mundo de 2006. No momento, as obras da Arena Palestra estão paradas por tempo indeterminado por conta da morte de um funcionário em acidente acontecido na última segunda-feira. A entrega, que estava prevista para o fim deste ano, deve ocorrer apenas em 2014. A obra está sendo feita pela WTorre, que, em troca, terá o direito de explorar comercialmente o estádio por 30 anos. A casa do Palmeiras, que terá capacidade para até 45 mil torcedores em dias de jogos de futebol, já conta com 80% de suas obras civis concluídas (o estádio está 63% pronto). O estádio também funcionará como uma arena multiuso para sediar shows e outros eventos, com capacidade para até 55 mil pessoas. O custo total da obra é de R$ 350 milhões.";
            String example7 = "Divulgados os vencedores do Prêmio Imprensa Embratel Foram premiadas reportagens em 5 categorias regionais e 12 nacionais. Reportagem do Globo Rural e série do Jornal das Dez receberam prêmio. G1, em São Paulo Tweet Foram divulgados nesta quinta-feira (2) os vencedores da 14ª edição do Prêmio Imprensa Embratel nas cinco categorias regionais e nas 12 nacionais. Confira aqui a lista dos vencedores. A reportagem &quot;Tinoco&quot; , do Globo Rural, da TV Globo, foi a vencedora na categoria reportagem cultural, e a série &quot;Juízes Ameaçados&quot; , produzida pelo Jornal das Dez, da Globo News, foi premiada na categoria reportagem de televisão. Na cerimônia de premiação, que acontecerá em 14 de maio, no Rio de Janeiro, será anunciado o vencedor do Grande Prêmio Barbosa Lima Sobrinho. Segundo a organização do prêmio, foram inscritas 1.862 reportagens de 860 jornalistas de todo o país. Foram registradas 1.577 reportagens nas 12 categorias nacionais e 285 nas cinco categorias regionais. No total será distribuído R$ 194 mil em premiações. Confira as reportagens vencedoras das categorias regionais e nacionais: Vencedora categoria Reportagem Regional Centro-Oeste Título reportagem: “Fim do 14º e 15º salários” Data publicação: 23/02/2012 Jornal Correio Braziliense Equipe: João Valadares, Adriana Caitanto, Karla Korreia, Renata Mariz, Junia Gama, Ricardo Taffner e Lilian Tahan. Vencedora categoria Reportagem Regional Nordeste Título da reportagem: “Paraíso às avessas” Data da publicação: 26/06/2011 Equipe: Ciara Carvalho e Fotógrafo Ricardo B. Labastier. Vencedora categoria Reportagem Regional Norte Título da reportagem: “Cheia do Século - Estamos prontos para outra?” Data da publicação: 05/06/2012 Jornal A Crítica - Caderno Especial Equipe: Elaíze Farias, Leandro Prazeres, Carolina Silva, Cimone Barros, Jonas Santos, Adauto Silva e Carla Yael.(editora) Vencedora categoria Reportagem Regional Sudeste Título da Reportagem: “Morte S/A” Data da publicação: 30/10/2011 Equipe: Mateus Parreiras, Valquiria Lopes e Luiz Ribeiro Vencedora categoria Reportagem Regional Sul Título da Reportagem: “Polícia fora da lei” Data da publicação: 20/05/2012 Equipe: Mauri König, Diego Ribeiro, Felippe Aníbal e Albari Rosa. Vencedora categoria Reportagem Cinematográfica Título da Reportagem: “BRS Presidente Vargas” Data da publicação: 17/04/2012 TV BRASIL - RJ ( Repórter Rio) Equipe: Marco Motta ( repórter cinematográfico); Júlio (repórter) e Bruno ( auxiliar e motorista). Vencedora categoria Reportagem Cultural Data da publicação: 13/05/2012 TV Globo – Globo Rural Equipe: Repórter José Hamilton Ribeiro; Produção, José Augusto Bezerra; Operador de áudio, Wilson Berzuini; Edição, Maurino Marques; Editores, Orlando Daniel e Olympio Giuzio; Cinegrafista, Jorge dos Santos; Arte, Fernando César. Vencedora categoria Reportagem Econômica Título da Reportagem: “Onde o Brasil desponta” Data da publicação: 14/12/2011 Revista Exame Equipe: Mariana Segala, Eduardo Salgado, Luciene Antunes, Patrick Cruz, Lucas Vettorazzo, Daniel Barros, Angela Pimenta, Lucas Amorim e Tatiana Gianini Vencedora categoria Reportagem de Rádio Título da Reportagem: “Os 50 anos da renúncia de Jânio Quadros” Data da publicação: 22/08/2011 Rádio Senado Equipe: Repórter, Adriano Faria; Produção, Jefferson Dalmoro; Áudio, Josevaldo Souza e Carlos Xavier; Edição, Ester Monteiro Vencedora categoria Reportagem de Televisão Título da Reportagem (série): “Juízes Ameaçados&quot; Data da publicação: 06/08/2012 TV Globo News (Jornal da dez) Equipe: Repórter Rodrigo Carvalho; Cinegrafista Egledio Vianna; editora de texto, Ana Terra Athayde; editor de imagem, Felipe Martins; produção, Inês Valladão. Vencedora categoria Reportagem em Jornal/Revista/Internet (tema livre) Título da Reportagem: “Filhos da rua” Data da publicação: 17/06/2012 Equipe: Letícia Duarte (Repórter) e Jefferson Botega (Fotógrafo). Vencedora categoria Reportagem Esportiva Título da Reportagem: “Os negócios suspeitos e a queda de Ricardo Teixeira” Data da publicação: 15/02/2012 Jornal Folha de São Paulo Equipe: Leandro Colon, Filipe Coutinho, Júlio Wiziack, Rodrigo Mattos e Sérgio Rangel Vencedora categoria Reportagem Fotográfica.";
            String example8 = "Pepsi retira do ar comercial acusado de racista Vídeo mostra mulher branca vítima de agressão tendo de identificar agressor em meio a grupo de negros e um bode. Da BBC Tweet A empresa de refrigerantes PepsiCo retirou do ar um comercial em meio a críticas de que o anúncio usa estereótipos raciais e banaliza a violência contra a mulher. O comercial postado na internet mostra uma mulher e um policial em uma delegacia. O policial pede à mulher, que foi vítima de uma agressão e usa muletas e colete ortopédico, para que ela identifique seu agressor entre um grupo de suspeitos enfileirados, formado por homens negros e um bode. O vídeo de 60 segundos foi descrito como &quot;provavelmente o comercial mais racista da história&quot; pelo acadêmico negro americano Boyce Watkins. Após ter retirado o anúncio do ar, a PepsiCo se desculpou pelo anúncio de seu refrigerante Mountain Dew. No anúncio, o bode ameaça atacar a mulher quando ele sair da prisão, caso ela o identifique para a polícia. Em meio às ameaças do bode, o policial pressiona a mulher para que ela identifique seu agressor. O policial usa a expressão &quot;do it&quot; (faça-o) um jogo de palavras com o termo &quot;dew&quot;, do nome do refrigerante. 'Total responsabilidade' A mulher começa a repetir que não tem como fazê-lo e acaba saindo correndo, aos gritos, da chefatura de polícia. Em um comunicado divulgado na quarta-feira, a PepsiCo disse que assumia &quot;total responsabilidade&quot; por qualquer ofensa causada pelo comercial e disse ter retirado o anúncio de seus sites online. Um dos criadores do argumento do comercial foi o rapper americano Tyler the Creator, que já foi criticado no passado por ter assinado letras supostamente misóginas e homofóbicas com a sua trupe de rap, Odd Future. Em sua defesa, o rapper disse que o anúncio fazia parte de uma série de comerciais criados para a PepsiCo e que o anúncio que o antecedeu mostrava a mulher vista na delegacia trabalhando como garçonete, quando era agredida pelo bode. veja também Ypê aparece na 2ª colocação, seguida por Colgate, Omo e Tang. Marca também lidera no ranking mundial da Kantar Worldpanel. Thu May 02 2013 13:03:19 -0300 02/05/2013 Empresa teve lucro líquido de US$ 3,1 milhões no primeiro trimestre. Faturamento com publicidade teve queda de 11,2% no período.";
            String example9 = "A multa foi aplicada em novembro de 2011 com base no Código de Defesa do Consumidor, que proíbe a publicidade abusiva, após o Instituto Alana questionar a prática de venda de lanches com brinquedos. Em nota, a Arcos Dourados informou que &quot;vai discutir a multa aplicada em 2011 no Poder Judiciário”. Segundo o Instituto Alana, a venda de alimentos com brinquedos para crianças &quot;cria uma lógica de consumo prejudicial e incentiva a formação de valores distorcidos, bem como a formação de hábitos alimentares prejudiciais à saúde”. No comunicado divulgado nesta segunda-feira, o McDonald’s destaca que, desde 2006, os brinquedos podem ser adquiridos sem obrigatoriedade de consumo de produtos e que todas as composições do McLanche Feliz apresentam 600 calorias, recomendação da Organização Mundial de Saúde para 1/3 das refeições diárias de uma criança. A empresa afirma ainda que possui um código de autorregulamentação em conjunto com empresas do setor, que prevê &quot;uma série de regras de comunicação para com o público infantil&quot;. O valor da multa fixado em de R$ 3,192 milhões refere-se ao valor máximo previsto pela legislação na época da denúncia, levando-se em conta a gravidade da conduta, a vantagem que a empresa tirou e a condição econômica da rede, segundo o Procon.";
            String example10 = "assista à entrevista exclusiva para o site do GNT Por Bruna Capistrano Vídeo: Carol Santos Se você pergunta a Nigella se ela está com fome, a resposta é &quot;sempre&quot;. Não é uma regra, mas quando a britânica comenta que pode escolher entre ficar na cozinha e sair para jogar bola com os filhos no parque, ela prefere &quot;chamar para ir à cozinha e preparar cupcakes&quot;. Nigella renega o título de chef e veste a camisa da cozinheira caseira que valoriza as refeições em família. Jornalista e com a sinceridade à flor da pele, ela conta que aprendeu a cozinhar com a mãe, já foi crítica de cozinha no &quot;The Sunday Times&quot; e não se prende a rótulos. &quot;O importante é a comida ser gostosa&quot;, comenta ela em entrevista exclusiva ao site do GNT durante visita ao Brasil para lançar o livro &quot;Na Cozinha com Nigella - receitas do coração da casa&quot;, da editora Best Seller . Prepare-se: dia 4 de julho, Nigellissima, o novo programa de Nigella Lawson, estreia no GNT logo na sequência da exibição do 'Que Marravilha! Especial com Nigella e Claude Troisgros. Ela conta um pouquinho do que vem por aí, assista abaixo: 1 Segredos para ter uma pele linda Nigella preza pela simplicidade e tem um dos segredos mais básicos para manter a pele linda e hidratada: beber muita água. Mas muita água não é apenas o litro e meio ou dois que os médicos recomendam. Nigella conta que bebe de três a quatro litros, evita o sol, faz pilates e se exercita cinco vezes por semana. Tudo para não fechar a boca para as comidas. &quot;Acho que comida faz bem para a pele. Acho que pessoas que não comem costumam ter a pele muito seca. Eu sempre penso que comer bacon e manteiga é como se eu estivesse colocando um hidratante para dentro. Então eu acho que faz bem, acho que comida faz bem. Eu não tenho a pele perfeita, mas não gosto muito de tomar sol. E eu bebo muita água, isso ajuda. Costumo beber de três a quatro litros de água por dia.&quot;. 2 Ingredientes 'must have' na cozinha de Nigella Sal marinho e limão siciliano são duas paixões da britânica. Mas não pense que ela está se referindo somente ao suco do limão para dar um sabor diferente ao prato. Aliás, nunca diga isso perto de Nigella! &quot;Eu odeio quando as pessoas utilizam só o suco.&quot;. 3 Os pontos positivos de levar os filhos para a cozinha Mãe de dois filhos, Cosima Thomasina (19 anos) e Bruno Paul (16 anos), Nigella sempre fez questão de cozinhar com a presença dos filhos. &quot;Eu cozinho com meus filhos, mas fazia isso principalmente quando eles eram mais novos porque as crianças precisam de entretenimento. Os dias são muito longos e eu não sou muito atleta&quot;, conta a britânica. Aventurar-se no mundo da culinária aproxima as crianças de diferentes tipos de alimentos e afasta as frescuras na hora da refeição. &quot;Quanto mais você envolve a criança com a cozinha, mais aventurosos eles serão na hora de comer.&quot;. 4 Momento de obsessão com caramelos salgados A mistura doce e salgado aguça o paladar de Nigella. E uma das comidinhas que mais deixam a britânica feliz é misturar caramelo com sal marinho. Ela até dá a receita de como preparar o mimo. &quot;Faço uma calda de caramelo e coloco sal, manteiga, açúcar e calda de bordo (ou syroup). Deixo ferver, coloco creme de leite e deixo engrossar. Por fim, adiciono sal marinho. É uma delícia!&quot;. 5 Pode ou não pode usar ingredientes prontos? Na cozinha de Nigella você pode usar o ingrediente que quiser. Basta ser gostoso, dar prazer de comer e ter uma boa aparência. &quot;Acho que a maioria das pessoas que acha que você não pode comprar caldo pronto pensa assim porque não cozinha todo dia e não tem que lavar toda aquela louça. Eu adoro preparar caldos. Se eu asso um frango, eu faço o caldo. Mas eu mantenho caldos prontos em casa.&quot;. 6 Comidas da infância são inspiração para os pratos Quem não lembra do bombom de chocolate com coco empanado? Uma das receitas mais famosas da Nigella no Brasil não é uma invenção da cozinheira, mas uma adaptação ao gosto da britânica, já que na Escócia eles têm costume de empanar barras de chocolate. &quot;Eu não sei como eu penso nessas coisas mas estou sempre pensando no que comer. Às vezes, consigo ideias quando lembro do que eu gostava de comer quando era pequena. Por exemplo, uma coisa que não está mais disponível hoje em dia. Penso: 'como eu posso fazer ou como posso criar uma coisa parecida?'&quot;. 7 Nem aí para as tendências... &quot;Quando você chega em casa à noite, você quer cozinhar o seu prato e ter a ideia de que aquilo é comida, de que aquilo é confortante&quot;. É assim que Nigella se vê livre das amarras das tendências criadas constantemente pelos chefs de cozinha, como optar pelos ingredientes crus, servidos em espuma ou com pouca cocção. &quot;As tendências não fazem diferença para mim&quot;, sentencia. 8 Feijoada em azul, vermelho e branco Se você ama comida, a pessoa certa para horas de conversa é Nigella. Apaixonada por texturas e sabores, ela conhece alguns ingredientes e pratos brasileiros. Quando conheceu a feijoada, já pensou em uma maneira de prepará-la em casa. &quot;Penso, por exemplo, como posso fazer uma versão da feijoada que possa ser compreensível para alguém que nunca esteve no Brasil. Talvez tenha um jeito de fazer a receita menos pesada e mais rápida. Então eu preciso ir para a cozinha e ver como fazer isso&quot;, diz Nigella. 9 Nigella, Jamie Oliver e Gordon Ramsay: o mundo voltado para a cozinha britânica Ao contrário de Jamie Oliver e Gordon Ramsay, Nigella deixa claro que não é chef e pensa em pratos voltados para a cozinha caseira, com ritmo diferente dos chefs em restaurantes. Por isso ela se sente tão à vontade de cozinhar sem se preocupar com a opinião de críticos, chefs e telespectadores. &quot;Quando faço meu programa, eu não penso no telespectador. Eu estou apenas cozinhando e fazendo o que eu faço. Se eu começar a pensar o que as pessoas pensam sobre mim, vou me tornar mais autoconsciente e ficar menos relaxada&quot;, ressalta. 10 Nigellissima vem aí com receitas italianas Foi em Florença que Nigella morou quando adolescente e aprendeu boa parte de suas inspirações gastronômicas. &quot;Acho que a comida do programa novo vai significar muito para os brasileiros porque os sabores são muito parecidos. Muitas receitas são bem rápidas e feitas de formas bem simples porque assim que a comida italiana é. Duas receitas que acho que vão funcionar por aqui são o ovo poché e o sanduíche de sorvete. Posso estar errada, mas é o que eu acho. Steve Jobs costumava dizer que a 'simplicidade é o máximo da sofisticação'&quot;. você também vai gostar de";
            String example11 = "Alessandra Ambrosio esteve na segunda-feira (17) no El Capitan Theater de Hollywood, Califórnia, para a estreia da nova animação do Monstros SA. A modelo levou a filha mais velha, Anja, de 4 anos, e as duas usaram vestidos brancos. A cantora Gwen Stefani também foi acompanhada da família. Ela estava com o marido, o roqueiro Gavin Rossdale, e os dois filhos, Kingston e Zuma. A animação Universidade Monstros estreia no Brasil nesta sexta-feira (21).";
            URI example = new URI(serverURI.toString() + "annotate?text=" + URLEncoder.encode( example11 , "UTF-8" ) + "&confidence=0.0&support=0" ) ;
            //URI example = new URI(serverURI.toString() + example2);

            java.awt.Desktop.getDesktop().browse(example);
        }
        catch (Exception e) {
            System.err.println("Could not open browser. " + e);
        }

        Thread warmUp = new Thread() {
            public void run() {
                //factory.searcher().warmUp((int) (configuration.getMaxCacheSize() * 0.7));
            }
        };
        warmUp.start();


        while(running) {
            Thread.sleep(100);
        }

        //Stop the HTTP server
        //server.stop(0);
        threadSelector.stopEndpoint();
        System.exit(0);

    }


    private static void setSpotters(Map<SpotterPolicy,Spotter> s) throws InitializationException {
        if (spotters.size() == 0)
            spotters = s;
        else
            throw new InitializationException("Trying to overwrite singleton Server.spotters. Something fishy happened!");
    }

    private static void setDisambiguators(Map<SpotlightConfiguration.DisambiguationPolicy,ParagraphDisambiguatorJ> s) throws InitializationException {
        if (disambiguators.size() == 0)
            disambiguators = s;
        else
            throw new InitializationException("Trying to overwrite singleton Server.disambiguators. Something fishy happened!");
    }

    public static Spotter getSpotter(String name) throws InputException {
        SpotterPolicy policy = SpotterPolicy.Default;
        try {
            policy = SpotterPolicy.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new InputException(String.format("Specified parameter spotter=%s is invalid. Use one of %s.",name,SpotterPolicy.values()));
        }

        if (spotters.size() == 0)
            throw new InputException(String.format("No spotters were loaded. Please add one of %s.",spotters.keySet()));

        Spotter spotter = spotters.get(policy);
        if (spotter==null) {
            throw new InputException(String.format("Specified spotter=%s has not been loaded. Use one of %s.",name,spotters.keySet()));
        }
        return spotter;
    }

    public static ParagraphDisambiguatorJ getDisambiguator(String name) throws InputException {
        DisambiguationPolicy policy = DisambiguationPolicy.Default;
        try {
            policy = DisambiguationPolicy.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new InputException(String.format("Specified parameter disambiguator=%s is invalid. Use one of %s.",name,DisambiguationPolicy.values()));
        }

        if (disambiguators.size() == 0)
            throw new InputException(String.format("No disambiguators were loaded. Please add one of %s.",disambiguators.keySet()));

        ParagraphDisambiguatorJ disambiguator = disambiguators.get(policy);
        if (disambiguator == null)
            throw new InputException(String.format("Specified disambiguator=%s has not been loaded. Use one of %s.",name,disambiguators.keySet()));
        return disambiguator;

    }

//    public static Spotter getSpotter(SpotterPolicy policy) throws InputException {
//        Spotter spotter = spotters.get(policy);
//        if (spotters.size()==0 || spotter==null) {
//            throw new InputException(String.format("Specified spotter=%s has not been loaded. Use one of %s.",policy,spotters.keySet()));
//        }
//        return spotter;
//    }
//
//    public static ParagraphDisambiguatorJ getDisambiguator(DisambiguationPolicy policy) throws InputException {
//        ParagraphDisambiguatorJ disambiguator = disambiguators.get(policy);
//        if (disambiguators.size() == 0 || disambiguators == null)
//            throw new InputException(String.format("Specified disambiguator=%s has not been loaded. Use one of %s.",policy,disambiguators.keySet()));
//        return disambiguator;
//    }

    public static SpotlightConfiguration getConfiguration() {
        return configuration;
    }

    public static TextTokenizer getTokenizer() {
        return tokenizer;
    }

    public static void setTokenizer(TextTokenizer tokenizer) {
        Server.tokenizer = tokenizer;
    }

    public static CombineAllAnnotationFilters getCombinedFilters() {
        return combinedFilters;
    }

    public static void setCombinedFilters(CombineAllAnnotationFilters combinedFilters) {
        Server.combinedFilters = combinedFilters;
    }

    public static String getPrefixedDBpediaURL(DBpediaResource resource) {
        return namespacePrefix + resource.uri();
    }

    public static void setNamespacePrefix(String namespacePrefix) {
        Server.namespacePrefix = namespacePrefix;
    }
}
